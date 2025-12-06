package pin122.kursovaya.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.repository.AppointmentRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для работы с очередью в Redis
 * Использует Sorted Set для хранения очереди: queue:doctor:{doctorId}
 * Score = позиция в очереди, Member = patient:{patientId}
 */
@Service
public class RedisQueueService {

    private static final String QUEUE_KEY_PREFIX = "queue:doctor:";
    
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> removeAndShiftScript;
    private final AppointmentRepository appointmentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public RedisQueueService(RedisTemplate<String, String> redisTemplate,
                            DefaultRedisScript<Long> removeAndShiftScript,
                            AppointmentRepository appointmentRepository,
                            SimpMessagingTemplate messagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.removeAndShiftScript = removeAndShiftScript;
        this.appointmentRepository = appointmentRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Добавляет пациента в очередь к врачу
     * @param patientId ID пациента
     * @param doctorId ID врача
     * @param position Позиция в очереди (score в Sorted Set)
     */
    public void addToQueue(Long patientId, Long doctorId, Integer position) {
        String queueKey = getQueueKey(doctorId);
        String patientKey = "patient:" + patientId;
        
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(queueKey, patientKey, position);
        
        // Отправляем уведомление об обновлении очереди
        notifyQueueUpdated(doctorId);
    }

    /**
     * Удаляет пациента из очереди с автоматическим сдвигом позиций
     * Использует Lua-скрипт для атомарной операции
     * 
     * @param patientId ID пациента
     * @param doctorId ID врача
     * @return true если пациент был удален, false если не найден
     */
    public boolean removeFromQueue(Long patientId, Long doctorId) {
        String queueKey = getQueueKey(doctorId);
        String patientKey = "patient:" + patientId;
        
        Long result = redisTemplate.execute(
            removeAndShiftScript,
            List.of(queueKey),
            patientKey
        );
        
        if (result != null && result > 0) {
            // Отправляем уведомление об обновлении очереди
            notifyQueueUpdated(doctorId);
            return true;
        }
        
        return false;
    }

    /**
     * Получает очередь к врачу
     * @param doctorId ID врача
     * @return Список записей очереди
     */
    public List<QueueEntryDto> getQueueByDoctor(Long doctorId) {
        String queueKey = getQueueKey(doctorId);
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        
        // Получаем все элементы с их позициями (score)
        Set<ZSetOperations.TypedTuple<String>> members = zSetOps.rangeWithScores(queueKey, 0, -1);
        
        if (members == null) {
            return List.of();
        }
        
        return members.stream()
                .map(tuple -> {
                    String patientKey = tuple.getValue();
                    Long patientId = extractPatientId(patientKey);
                    Integer position = tuple.getScore() != null ? tuple.getScore().intValue() : 0;
                    
                    // Получаем appointment для этого пациента и врача
                    Appointment appointment = findAppointment(patientId, doctorId);
                    
                    return new QueueEntryDto(
                            null, // ID не используется в Redis
                            doctorId,
                            appointment != null ? appointment.getId() : null,
                            patientId,
                            position,
                            OffsetDateTime.now()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Получает позицию пациента в очереди
     * @param patientId ID пациента
     * @param doctorId ID врача
     * @return Позиция в очереди или null если не найден
     */
    public Integer getPatientPosition(Long patientId, Long doctorId) {
        String queueKey = getQueueKey(doctorId);
        String patientKey = "patient:" + patientId;
        
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        Double score = zSetOps.score(queueKey, patientKey);
        
        return score != null ? score.intValue() : null;
    }

    /**
     * Проверяет, является ли пациент следующим в очереди
     * @param patientId ID пациента
     * @param doctorId ID врача
     * @return true если пациент следующий (позиция 0 или нет пациентов перед ним)
     */
    public boolean isPatientNextInQueue(Long patientId, Long doctorId) {
        Integer position = getPatientPosition(patientId, doctorId);
        if (position == null) {
            return false;
        }
        
        if (position == 0) {
            return true;
        }
        
        // Проверяем, есть ли пациенты с позицией меньше текущей
        String queueKey = getQueueKey(doctorId);
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        Long count = zSetOps.count(queueKey, 0, position - 1);
        
        return count == null || count == 0;
    }

    /**
     * Получает размер очереди к врачу
     * @param doctorId ID врача
     * @return Количество пациентов в очереди
     */
    public Long getQueueSize(Long doctorId) {
        String queueKey = getQueueKey(doctorId);
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        return zSetOps.zCard(queueKey);
    }

    /**
     * Очищает очередь к врачу
     * @param doctorId ID врача
     */
    public void clearQueue(Long doctorId) {
        String queueKey = getQueueKey(doctorId);
        redisTemplate.delete(queueKey);
        notifyQueueUpdated(doctorId);
    }

    /**
     * Отправляет WebSocket уведомление об обновлении очереди
     * @param doctorId ID врача
     */
    private void notifyQueueUpdated(Long doctorId) {
        List<QueueEntryDto> queue = getQueueByDoctor(doctorId);
        
        // Отправляем обновление всем подписчикам на очередь этого врача
        messagingTemplate.convertAndSend(
            "/topic/queue/doctor/" + doctorId,
            new QueueUpdateEvent(doctorId, queue)
        );
    }

    /**
     * Получает все очереди для конкретного пациента
     * @param patientId ID пациента
     * @return Список записей очереди для всех врачей
     */
    public List<QueueEntryDto> getQueuesByPatient(Long patientId) {
        // Получаем все appointments пациента
        List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
        
        // Группируем по врачам и получаем очереди
        return appointments.stream()
                .filter(a -> a.getDoctor() != null)
                .filter(a -> !"completed".equals(a.getStatus()) && !"cancelled".equals(a.getStatus()))
                .collect(Collectors.groupingBy(Appointment::getDoctor))
                .entrySet().stream()
                .flatMap(entry -> {
                    Long doctorId = entry.getKey().getId();
                    Integer position = getPatientPosition(patientId, doctorId);
                    if (position != null) {
                        // Получаем очередь к врачу и фильтруем по пациенту
                        List<QueueEntryDto> queue = getQueueByDoctor(doctorId);
                        return queue.stream()
                                .filter(e -> patientId.equals(e.getPatientId()));
                    }
                    return java.util.stream.Stream.empty();
                })
                .collect(Collectors.toList());
    }

    /**
     * Строит очередь из appointments для пациента
     * Синхронизирует Redis с данными из PostgreSQL
     * 
     * @param patientId ID пациента
     */
    public void buildQueueFromAppointments(Long patientId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoffTime = now.minusMinutes(20);
        
        // Получаем все активные appointments пациента
        List<Appointment> patientAppointments = appointmentRepository
                .findUpcomingAppointmentsByPatient(patientId, cutoffTime)
                .stream()
                .filter(a -> !"completed".equals(a.getStatus()) && !"cancelled".equals(a.getStatus()))
                .collect(Collectors.toList());
        
        if (patientAppointments.isEmpty()) {
            return;
        }
        
        // Группируем по врачам
        patientAppointments.stream()
                .collect(Collectors.groupingBy(Appointment::getDoctor))
                .forEach((doctor, appointments) -> {
                    Long doctorId = doctor.getId();
                    
                    // Удаляем старую запись пациента из очереди этого врача
                    removeFromQueue(patientId, doctorId);
                    
                    // Получаем все активные appointments к этому врачу для расчета позиций
                    List<Appointment> allDoctorAppointments = appointmentRepository
                            .findByDoctorId(doctorId)
                            .stream()
                            .filter(a -> a.getPatient() != null
                                    && a.getStartTime().isAfter(cutoffTime)
                                    && !"completed".equals(a.getStatus())
                                    && !"cancelled".equals(a.getStatus()))
                            .sorted((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime()))
                            .collect(Collectors.toList());
                    
                    // Добавляем пациента в очередь для каждого его appointment
                    for (Appointment appointment : appointments) {
                        // Рассчитываем позицию: сколько appointments имеют более раннее время
                        long position = allDoctorAppointments.stream()
                                .filter(a -> a.getStartTime().isBefore(appointment.getStartTime()))
                                .count();
                        
                        addToQueue(patientId, doctorId, (int) position);
                    }
                });
    }

    private String getQueueKey(Long doctorId) {
        return QUEUE_KEY_PREFIX + doctorId;
    }

    private Long extractPatientId(String patientKey) {
        if (patientKey != null && patientKey.startsWith("patient:")) {
            try {
                return Long.parseLong(patientKey.substring("patient:".length()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Appointment findAppointment(Long patientId, Long doctorId) {
        if (patientId == null || doctorId == null) {
            return null;
        }
        
        return appointmentRepository
                .findByPatientId(patientId)
                .stream()
                .filter(a -> doctorId.equals(a.getDoctor().getId()))
                .filter(a -> !"completed".equals(a.getStatus()) && !"cancelled".equals(a.getStatus()))
                .findFirst()
                .orElse(null);
    }

    /**
     * DTO для WebSocket уведомлений об обновлении очереди
     */
    public static class QueueUpdateEvent {
        private Long doctorId;
        private List<QueueEntryDto> queue;

        public QueueUpdateEvent(Long doctorId, List<QueueEntryDto> queue) {
            this.doctorId = doctorId;
            this.queue = queue;
        }

        public Long getDoctorId() {
            return doctorId;
        }

        public void setDoctorId(Long doctorId) {
            this.doctorId = doctorId;
        }

        public List<QueueEntryDto> getQueue() {
            return queue;
        }

        public void setQueue(List<QueueEntryDto> queue) {
            this.queue = queue;
        }
    }
}

