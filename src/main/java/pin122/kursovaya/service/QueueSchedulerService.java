package pin122.kursovaya.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.dto.WebSocketSessionData;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scheduled сервис для проверки и очистки просроченных appointments из очереди
 * Запускается каждую минуту и проверяет все активные сессии
 * 
 * @EnableScheduling уже включен в KursovayaApplication
 */
@Service
public class QueueSchedulerService {

    private final RedisQueueService redisQueueService;

    public QueueSchedulerService(RedisQueueService redisQueueService) {
        this.redisQueueService = redisQueueService;
    }

    /**
     * Проверяет все активные сессии и удаляет из очереди Redis appointments,
     * время которых уже прошло
     * 
     * Запускается каждую минуту (60000 мс)
     * 
     * ВАЖНО: Удаляет только из очереди Redis, НЕ меняет статус в БД
     */
    @Scheduled(fixedRate = 60000)
    public void checkExpiredAppointments() {
        System.out.println("DEBUG Scheduler: Начало проверки просроченных appointments");
        
        try {
            // Получаем все активные сессии
            List<WebSocketSessionData> activeSessions = redisQueueService.getAllActiveSessions();
            
            if (activeSessions.isEmpty()) {
                System.out.println("DEBUG Scheduler: Нет активных сессий, пропускаем проверку");
                return;
            }
            
            System.out.println("DEBUG Scheduler: Найдено активных сессий: " + activeSessions.size());
            
            int totalRemoved = 0;
            Set<Long> affectedDoctorIds = new java.util.HashSet<>();
            
            for (WebSocketSessionData session : activeSessions) {
                // Удаляем просроченные appointments для каждой сессии
                int removed = redisQueueService.removeExpiredAppointments(session.getSessionId());
                totalRemoved += removed;
                
                if (removed > 0 && session.getEmail() != null) {
                    // Получаем обновленные очереди для пациента
                    List<QueueEntryDto> updatedQueues = redisQueueService.getQueuesByPatient(session.getPatientId());
                    
                    // Собираем ID врачей для последующего уведомления
                    updatedQueues.forEach(q -> affectedDoctorIds.add(q.getDoctorId()));
                    
                    // Отправляем обновление пользователю
                    redisQueueService.notifyUserQueueUpdate(session.getEmail(), updatedQueues);
                    
                    System.out.println("DEBUG Scheduler: Удалено " + removed + 
                            " просроченных записей для сессии " + session.getSessionId());
                }
            }
            
            // Уведомляем всех подписчиков об изменениях в очередях врачей
            for (Long doctorId : affectedDoctorIds) {
                redisQueueService.notifyQueueUpdated(doctorId);
            }
            
            System.out.println("DEBUG Scheduler: Проверка завершена, всего удалено записей: " + totalRemoved);
            
        } catch (Exception e) {
            System.err.println("DEBUG Scheduler: Ошибка при проверке просроченных appointments: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Пересчитывает позиции во всех активных очередях
     * Запускается каждые 5 минут для синхронизации
     */
    @Scheduled(fixedRate = 300000)
    public void recalculateAllQueues() {
        System.out.println("DEBUG Scheduler: Начало пересчета всех очередей");
        
        try {
            List<WebSocketSessionData> activeSessions = redisQueueService.getAllActiveSessions();
            
            // Собираем уникальные ID врачей из всех сессий
            Set<Long> doctorIds = activeSessions.stream()
                    .filter(s -> s.getPatientId() != null)
                    .flatMap(s -> redisQueueService.getQueuesByPatient(s.getPatientId()).stream())
                    .map(QueueEntryDto::getDoctorId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            
            // Пересчитываем очередь для каждого врача
            for (Long doctorId : doctorIds) {
                redisQueueService.recalculateQueueForDoctor(doctorId);
            }
            
            System.out.println("DEBUG Scheduler: Пересчет завершен для " + doctorIds.size() + " врачей");
            
        } catch (Exception e) {
            System.err.println("DEBUG Scheduler: Ошибка при пересчете очередей: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

