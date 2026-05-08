package pin122.kursovaya.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.dto.WebSocketSessionData;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Периодические задания для очереди в Redis: удаление просроченных приёмов из ZSET и пересчёт позиций.
 *
 * <p>Включающая аннотация планировщика задаётся в классе приложения ({@code @EnableScheduling}).
 */
@Service
public class QueueSchedulerService {

    private final RedisQueueService redisQueueService;

    /**
     * @param redisQueueService доступ к очередям Redis и уведомлениям WebSocket
     */
    public QueueSchedulerService(RedisQueueService redisQueueService) {
        this.redisQueueService = redisQueueService;
    }

    /**
     * Для каждой активной WebSocket-сессии удаляет из Redis просроченные приёмы и уведомляет клиентов.
     *
     * <p>Период: 60 с. Статусы записей в БД не изменяются.
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
            Set<String> affectedDoctorDays = new HashSet<>();

            for (WebSocketSessionData session : activeSessions) {
                int removed = redisQueueService.removeExpiredAppointments(session.getSessionId(), affectedDoctorDays);
                totalRemoved += removed;

                if (removed > 0 && session.getEmail() != null) {
                    List<QueueEntryDto> updatedQueues = redisQueueService.getQueuesByPatient(session.getPatientId());

                    redisQueueService.notifyUserQueueUpdate(session.getEmail(), updatedQueues);

                    System.out.println("DEBUG Scheduler: Удалено " + removed +
                            " просроченных записей для сессии " + session.getSessionId());
                }
            }

            for (String key : affectedDoctorDays) {
                String[] p = key.split("\\|", 2);
                if (p.length == 2) {
                    redisQueueService.notifyQueueUpdated(Long.parseLong(p[0]), LocalDate.parse(p[1]));
                }
            }
            
            System.out.println("DEBUG Scheduler: Проверка завершена, всего удалено записей: " + totalRemoved);
            
        } catch (Exception e) {
            System.err.println("DEBUG Scheduler: Ошибка при проверке просроченных appointments: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Пересчитывает очереди Redis для всех пациентов с активными сессиями (синхронизация с БД).
     *
     * <p>Период: 5 минут.
     */
    @Scheduled(fixedRate = 300000)
    public void recalculateAllQueues() {
        System.out.println("DEBUG Scheduler: Начало пересчета всех очередей");
        
        try {
            redisQueueService.recalculateQueuesForAllActiveSessionPatients();

            System.out.println("DEBUG Scheduler: Пересчет очередей по сессиям завершен");
            
        } catch (Exception e) {
            System.err.println("DEBUG Scheduler: Ошибка при пересчете очередей: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

