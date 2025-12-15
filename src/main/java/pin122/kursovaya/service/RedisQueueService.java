package pin122.kursovaya.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.dto.WebSocketSessionData;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.repository.AppointmentRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сервис для работы с очередью в Redis
 * Использует Sorted Set для хранения очереди: queue:doctor:{doctorId}
 * Score = позиция в очереди, Member = patient:{patientId}
 * 
 * Также управляет WebSocket сессиями:
 * - ws:session:{sessionId} - JSON с данными сессии
 * - ws:sessions:active - Set активных sessionId
 * - patient:sessions:{patientId} - Set сессий пациента (для multi-device)
 */
@Service
public class RedisQueueService {

    private static final String QUEUE_KEY_PREFIX = "queue:doctor:";
    private static final String SESSION_KEY_PREFIX = "ws:session:";
    private static final String ACTIVE_SESSIONS_KEY = "ws:sessions:active";
    private static final String PATIENT_SESSIONS_PREFIX = "patient:sessions:";
    
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> removeAndShiftScript;
    private final AppointmentRepository appointmentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisQueueService(RedisTemplate<String, String> redisTemplate,
                            DefaultRedisScript<Long> removeAndShiftScript,
                            AppointmentRepository appointmentRepository,
                            SimpMessagingTemplate messagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.removeAndShiftScript = removeAndShiftScript;
        this.appointmentRepository = appointmentRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // ==================== SESSION MANAGEMENT ====================

    /**
     * Генерирует уникальный ID сессии
     */
    public String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Сохраняет данные сессии в Redis
     * @param sessionId ID сессии
     * @param sessionData Данные сессии
     */
    public void saveSession(String sessionId, WebSocketSessionData sessionData) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            String jsonData = objectMapper.writeValueAsString(sessionData);
            
            // Сохраняем данные сессии
            redisTemplate.opsForValue().set(sessionKey, jsonData);
            
            // Добавляем в Set активных сессий
            redisTemplate.opsForSet().add(ACTIVE_SESSIONS_KEY, sessionId);
            
            // Добавляем в Set сессий пациента (для multi-device)
            if (sessionData.getPatientId() != null) {
                String patientSessionsKey = PATIENT_SESSIONS_PREFIX + sessionData.getPatientId();
                redisTemplate.opsForSet().add(patientSessionsKey, sessionId);
            }
            
            System.out.println("DEBUG Redis: Сессия сохранена: " + sessionId);
        } catch (JsonProcessingException e) {
            System.err.println("DEBUG Redis: Ошибка сериализации сессии: " + e.getMessage());
        }
    }

    /**
     * Получает данные сессии из Redis
     * @param sessionId ID сессии
     * @return Данные сессии или null если не найдена
     */
    public WebSocketSessionData getSession(String sessionId) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            String jsonData = redisTemplate.opsForValue().get(sessionKey);
            
            if (jsonData == null) {
                return null;
            }
            
            return objectMapper.readValue(jsonData, WebSocketSessionData.class);
        } catch (JsonProcessingException e) {
            System.err.println("DEBUG Redis: Ошибка десериализации сессии: " + e.getMessage());
            return null;
        }
    }

    /**
     * Удаляет сессию и связанные данные из Redis
     * @param sessionId ID сессии
     */
    public void deleteSession(String sessionId) {
        WebSocketSessionData sessionData = getSession(sessionId);
        
        if (sessionData != null) {
            // Удаляем из Set сессий пациента
            if (sessionData.getPatientId() != null) {
                String patientSessionsKey = PATIENT_SESSIONS_PREFIX + sessionData.getPatientId();
                redisTemplate.opsForSet().remove(patientSessionsKey, sessionId);
                
                // Проверяем, остались ли у пациента другие сессии
                Long remainingSessions = redisTemplate.opsForSet().size(patientSessionsKey);
                if (remainingSessions == null || remainingSessions == 0) {
                    // Если нет других сессий - удаляем пациента из всех очередей
                    removePatientFromAllQueues(sessionData.getPatientId());
                    // Удаляем пустой Set сессий пациента
                    redisTemplate.delete(patientSessionsKey);
                }
            }
        }
        
        // Удаляем данные сессии
        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.delete(sessionKey);
        
        // Удаляем из Set активных сессий
        redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_KEY, sessionId);
        
        System.out.println("DEBUG Redis: Сессия удалена: " + sessionId);
    }

    /**
     * Получает все активные сессии
     * @return Список данных всех активных сессий
     */
    public List<WebSocketSessionData> getAllActiveSessions() {
        Set<String> sessionIds = redisTemplate.opsForSet().members(ACTIVE_SESSIONS_KEY);
        
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        
        return sessionIds.stream()
                .map(this::getSession)
                .filter(session -> session != null)
                .collect(Collectors.toList());
    }

    /**
     * Получает все сессии пациента
     * @param patientId ID пациента
     * @return Set ID сессий
     */
    public Set<String> getPatientSessions(Long patientId) {
        String patientSessionsKey = PATIENT_SESSIONS_PREFIX + patientId;
        return redisTemplate.opsForSet().members(patientSessionsKey);
    }

    /**
     * Проверяет, есть ли у пациента активные сессии
     * @param patientId ID пациента
     * @return true если есть хотя бы одна активная сессия
     */
    public boolean hasActiveSessions(Long patientId) {
        String patientSessionsKey = PATIENT_SESSIONS_PREFIX + patientId;
        Long size = redisTemplate.opsForSet().size(patientSessionsKey);
        return size != null && size > 0;
    }

    // ==================== QUEUE MANAGEMENT FOR TODAY ====================

    /**
     * Формирует очередь для пациента на текущий день
     * Получает все appointments на сегодня, группирует по врачам,
     * проверяет позицию пользователя и добавляет в очередь Redis
     * 
     * @param patientId ID пациента
     * @return Список записей очереди для пациента
     */
    public List<QueueEntryDto> buildQueueForToday(Long patientId) {
        LocalDate today = LocalDate.now();
        OffsetDateTime startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime now = OffsetDateTime.now();
        
        System.out.println("DEBUG Redis: Построение очереди на сегодня для пациента " + patientId);
        System.out.println("DEBUG Redis: Диапазон: " + startOfDay + " - " + endOfDay);
        
        // Получаем все appointments на сегодня для всех врачей
        List<Appointment> allTodayAppointments = appointmentRepository.findByStartTimeBetween(startOfDay, endOfDay)
                .stream()
                .filter(a -> a.getPatient() != null)
                .filter(a -> !"completed".equals(a.getStatus()) && !"cancelled".equals(a.getStatus()))
                .filter(a -> a.getStartTime().isAfter(now)) // Только будущие приемы
                .collect(Collectors.toList());
        
        System.out.println("DEBUG Redis: Всего appointments на сегодня: " + allTodayAppointments.size());
        
        // Группируем по врачам
        var appointmentsByDoctor = allTodayAppointments.stream()
                .collect(Collectors.groupingBy(a -> a.getDoctor().getId()));
        
        List<QueueEntryDto> patientQueueEntries = new ArrayList<>();
        
        for (var entry : appointmentsByDoctor.entrySet()) {
            Long doctorId = entry.getKey();
            List<Appointment> doctorAppointments = entry.getValue();
            
            // Сортируем по времени начала
            doctorAppointments.sort((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime()));
            
            // Проверяем, есть ли текущий пациент в этих appointments
            Appointment patientAppointment = doctorAppointments.stream()
                    .filter(a -> patientId.equals(a.getPatient().getId()))
                    .findFirst()
                    .orElse(null);
            
            if (patientAppointment != null) {
                // Рассчитываем позицию: сколько appointments перед текущим пациентом
                int position = 0;
                for (Appointment appointment : doctorAppointments) {
                    if (appointment.getId().equals(patientAppointment.getId())) {
                        break;
                    }
                    position++;
                }
                
                // Добавляем пациента в очередь Redis
                addToQueue(patientId, doctorId, position);
                
                // Создаем DTO для возврата
                QueueEntryDto queueEntry = new QueueEntryDto(
                        null,
                        doctorId,
                        patientAppointment.getId(),
                        patientId,
                        position,
                        OffsetDateTime.now()
                );
                patientQueueEntries.add(queueEntry);
                
                System.out.println("DEBUG Redis: Пациент " + patientId + " добавлен в очередь к врачу " + 
                        doctorId + " на позицию " + position);
            }
        }
        
        return patientQueueEntries;
    }

    /**
     * Удаляет пациента из всех очередей
     * @param patientId ID пациента
     */
    public void removePatientFromAllQueues(Long patientId) {
        // Получаем все appointments пациента для определения врачей
        List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
        
        Set<Long> doctorIds = appointments.stream()
                .filter(a -> a.getDoctor() != null)
                .map(a -> a.getDoctor().getId())
                .collect(Collectors.toSet());
        
        for (Long doctorId : doctorIds) {
            removeFromQueue(patientId, doctorId);
        }
        
        System.out.println("DEBUG Redis: Пациент " + patientId + " удален из всех очередей");
    }

    /**
     * Удаляет просроченные appointments из очереди
     * Вызывается scheduled задачей каждую минуту
     * 
     * @param sessionId ID сессии для обновления
     * @return Количество удаленных записей
     */
    public int removeExpiredAppointments(String sessionId) {
        WebSocketSessionData session = getSession(sessionId);
        if (session == null || session.getAppointmentIds() == null) {
            return 0;
        }
        
        OffsetDateTime now = OffsetDateTime.now();
        int removedCount = 0;
        List<Long> activeAppointmentIds = new ArrayList<>();
        
        for (Long appointmentId : session.getAppointmentIds()) {
            Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
            
            if (appointment == null) {
                continue;
            }
            
            // Если время приема прошло - удаляем из очереди
            if (appointment.getStartTime().isBefore(now)) {
                if (appointment.getPatient() != null && appointment.getDoctor() != null) {
                    removeFromQueue(appointment.getPatient().getId(), appointment.getDoctor().getId());
                    removedCount++;
                    System.out.println("DEBUG Redis: Удален просроченный appointment " + appointmentId + 
                            " из очереди к врачу " + appointment.getDoctor().getId());
                }
            } else {
                activeAppointmentIds.add(appointmentId);
            }
        }
        
        // Обновляем список appointments в сессии
        if (removedCount > 0) {
            session.setAppointmentIds(activeAppointmentIds);
            saveSession(sessionId, session);
        }
        
        return removedCount;
    }

    /**
     * Пересчитывает очередь для врача после изменения статуса appointment
     * @param doctorId ID врача
     */
    public void recalculateQueueForDoctor(Long doctorId) {
        LocalDate today = LocalDate.now();
        OffsetDateTime startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime now = OffsetDateTime.now();
        
        // Получаем все активные appointments к врачу на сегодня
        List<Appointment> doctorAppointments = appointmentRepository.findByDoctorId(doctorId)
                .stream()
                .filter(a -> a.getPatient() != null)
                .filter(a -> a.getStartTime().isAfter(startOfDay) && a.getStartTime().isBefore(endOfDay))
                .filter(a -> a.getStartTime().isAfter(now))
                .filter(a -> !"completed".equals(a.getStatus()) && !"cancelled".equals(a.getStatus()))
                .sorted((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime()))
                .collect(Collectors.toList());
        
        // Очищаем текущую очередь к врачу
        clearQueue(doctorId);
        
        // Добавляем всех пациентов с новыми позициями
        int position = 0;
        for (Appointment appointment : doctorAppointments) {
            addToQueueWithoutNotification(appointment.getPatient().getId(), doctorId, position);
            position++;
        }
        
        // Отправляем одно уведомление об обновлении очереди
        notifyQueueUpdated(doctorId);
        
        System.out.println("DEBUG Redis: Очередь к врачу " + doctorId + " пересчитана, " + 
                doctorAppointments.size() + " пациентов");
    }

    // ==================== QUEUE OPERATIONS ====================

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
     * Добавляет пациента в очередь без отправки уведомления
     * Используется при массовом обновлении очереди
     */
    private void addToQueueWithoutNotification(Long patientId, Long doctorId, Integer position) {
        String queueKey = getQueueKey(doctorId);
        String patientKey = "patient:" + patientId;
        
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(queueKey, patientKey, position);
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
    }

    /**
     * Очищает очередь к врачу и отправляет уведомление
     * @param doctorId ID врача
     */
    public void clearQueueWithNotification(Long doctorId) {
        clearQueue(doctorId);
        notifyQueueUpdated(doctorId);
    }

    /**
     * Отправляет WebSocket уведомление об обновлении очереди
     * @param doctorId ID врача
     */
    public void notifyQueueUpdated(Long doctorId) {
        List<QueueEntryDto> queue = getQueueByDoctor(doctorId);
        
        // Отправляем обновление всем подписчикам на очередь этого врача
        messagingTemplate.convertAndSend(
            "/topic/queue/doctor/" + doctorId,
            new QueueUpdateEvent(doctorId, queue)
        );
    }

    /**
     * Отправляет персональное уведомление пользователю
     * @param email Email пользователя
     * @param queueEntries Записи очереди
     */
    public void notifyUserQueueUpdate(String email, List<QueueEntryDto> queueEntries) {
        messagingTemplate.convertAndSendToUser(
            email,
            "/queue/user",
            new QueueInitResponse(true, "Очередь обновлена", queueEntries)
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
     * Строит очередь из appointments для пациента (legacy метод)
     * @deprecated Используйте buildQueueForToday вместо этого метода
     */
    @Deprecated
    public void buildQueueFromAppointments(Long patientId) {
        buildQueueForToday(patientId);
    }

    // ==================== PRIVATE HELPERS ====================

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

    // ==================== DTO CLASSES ====================

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

    /**
     * DTO для ответа инициализации очереди
     */
    public static class QueueInitResponse {
        private boolean success;
        private String message;
        private List<QueueEntryDto> data;

        public QueueInitResponse(boolean success, String message, List<QueueEntryDto> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<QueueEntryDto> getData() { return data; }
        public void setData(List<QueueEntryDto> data) { this.data = data; }
    }
}
