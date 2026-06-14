package pin122.kursovaya.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.dto.WebSocketSessionData;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.PatientRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Очередь пациентов в Redis (ZSET по ключу {@code queue:doctor:{id}:{yyyy-MM-dd}}) и учёт WebSocket-сессий.
 *
 * <p>Очередь: score — позиция, member — {@code patient:{patientId}}. Сессии: {@code ws:session:…},
 * множество активных сессий, множество сессий на пациента для нескольких устройств.
 */
@Service
public class RedisQueueService {

    private static final String QUEUE_KEY_PREFIX = "queue:doctor:";
    private static final String SESSION_KEY_PREFIX = "ws:session:";
    private static final String ACTIVE_SESSIONS_KEY = "ws:sessions:active";
    private static final String PATIENT_SESSIONS_PREFIX = "patient:sessions:";
    private static final Set<String> TERMINAL_STATUSES = Set.of("completed", "cancelled", "no_show");

    public enum QueueScopeMode {
        TODAY,
        ALL;

        public static QueueScopeMode from(String value) {
            if (value == null || value.isBlank()) {
                return TODAY;
            }
            try {
                return QueueScopeMode.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return TODAY;
            }
        }
    }
    
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> removeAndShiftScript;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @param redisTemplate        операции со строковыми ключами Redis
     * @param removeAndShiftScript Lua-скрипт удаления из ZSET со сдвигом score
     * @param appointmentRepository чтение приёмов для построения и очистки очереди
     * @param messagingTemplate    рассылка STOMP/WebSocket
     */
    public RedisQueueService(RedisTemplate<String, String> redisTemplate,
                            DefaultRedisScript<Long> removeAndShiftScript,
                            AppointmentRepository appointmentRepository,
                            PatientRepository patientRepository,
                            SimpMessagingTemplate messagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.removeAndShiftScript = removeAndShiftScript;
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // ==================== SESSION MANAGEMENT ====================

    /**
     * Создаёт новый UUID для идентификатора WebSocket-сессии в Redis.
     *
     * @return строка UUID
     */
    public String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Сериализует {@link WebSocketSessionData} в JSON и сохраняет в Redis, регистрируя сессию в индексах.
     *
     * @param sessionId   ключ сессии
     * @param sessionData полезная нагрузка (email, patientId, список приёмов и т.д.)
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
     * Десериализует сессию из Redis по ключу.
     *
     * @param sessionId идентификатор сессии
     * @return объект данных или {@code null}, если ключ отсутствует или JSON повреждён
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
     * Удаляет сессию из Redis и при отсутствии других сессий пациента очищает его из всех очередей.
     *
     * @param sessionId идентификатор сессии
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
     * Загружает данные по всем sessionId из множества активных сессий.
     *
     * @return список {@link WebSocketSessionData} без {@code null}-элементов
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
     * Идентификаторы WebSocket-сессий, привязанных к пациенту.
     *
     * @param patientId идентификатор пациента
     * @return множество sessionId или {@code null}, если ключа нет в Redis
     */
    public Set<String> getPatientSessions(Long patientId) {
        String patientSessionsKey = PATIENT_SESSIONS_PREFIX + patientId;
        return redisTemplate.opsForSet().members(patientSessionsKey);
    }

    /**
     * Проверяет непустое множество сессий пациента в Redis.
     *
     * @param patientId идентификатор пациента
     * @return {@code true}, если размер множества {@code > 0}
     */
    public boolean hasActiveSessions(Long patientId) {
        String patientSessionsKey = PATIENT_SESSIONS_PREFIX + patientId;
        Long size = redisTemplate.opsForSet().size(patientSessionsKey);
        return size != null && size > 0;
    }

    // ==================== QUEUE MANAGEMENT FOR TODAY ====================

    /**
     * По приёмам на сегодня добавляет пациента в ZSET очередей к соответствующим врачам с рассчитанной позицией.
     *
     * @param patientId идентификатор пациента
     * @return список созданных {@link QueueEntryDto} для ответа клиенту
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
                .filter(a -> isActiveQueueAppointment(a, today, now))
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
                
                // Добавляем пациента в очередь Redis (ключ на календарный день)
                addToQueue(patientId, doctorId, position, today);
                
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
     * Пересобирает Redis-очереди пациента в выбранном режиме и возвращает актуальные позиции.
     *
     * @param patientId идентификатор пациента
     * @param mode      {@code TODAY} — только сегодня, {@code ALL} — все будущие активные приёмы
     * @return список позиций пациента
     */
    public List<QueueEntryDto> buildQueuesForPatient(Long patientId, QueueScopeMode mode) {
        QueueScopeMode effectiveMode = mode == null ? QueueScopeMode.TODAY : mode;
        if (effectiveMode == QueueScopeMode.TODAY) {
            return buildQueueForToday(patientId);
        }

        Set<QueueScope> scopes = appointmentRepository.findByPatientId(patientId)
                .stream()
                .filter(a -> isActiveQueueAppointment(a, null, OffsetDateTime.now()))
                .map(this::queueScopeOf)
                .filter(scope -> scope != null)
                .collect(Collectors.toSet());

        scopes.forEach(scope -> recalculateQueueForDoctor(scope.doctorId(), scope.queueDate(), false));
        return getQueuesByPatient(patientId, effectiveMode);
    }

    /**
     * Для каждого уникального (врач, день) из приёмов пациента вызывает {@link #removeFromQueue(Long, Long, LocalDate)}.
     *
     * @param patientId идентификатор пациента
     */
    public void removePatientFromAllQueues(Long patientId) {
        List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
        Set<String> seen = new HashSet<>();
        for (Appointment a : appointments) {
            if (a.getDoctor() == null) {
                continue;
            }
            LocalDate d = queueDateFromStart(a.getStartTime());
            String key = a.getDoctor().getId() + ":" + d;
            if (seen.add(key)) {
                removeFromQueue(patientId, a.getDoctor().getId(), d);
            }
        }
        System.out.println("DEBUG Redis: Пациент " + patientId + " удален из всех очередей (по дням)");
    }

    /**
     * Удаляет из очереди приёмы сессии, у которых время начала уже в прошлом; обновляет список id в сессии.
     *
     * @param sessionId идентификатор WebSocket-сессии
     * @return число удалений из очереди
     */
    public int removeExpiredAppointments(String sessionId) {
        return removeExpiredAppointments(sessionId, null);
    }

    /**
     * То же, что {@link #removeExpiredAppointments(String)}, с опциональной регистрацией затронутых пар (врач, день) для broadcast.
     *
     * @param sessionId             идентификатор сессии
     * @param affectedDoctorDayKeys коллекция для добавления строк {@code doctorId|yyyy-MM-dd} или {@code null}
     * @return число удалений из очереди
     */
    public int removeExpiredAppointments(String sessionId, Collection<String> affectedDoctorDayKeys) {
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

            if (appointment.getStartTime().isBefore(now)) {
                if (appointment.getPatient() != null && appointment.getDoctor() != null) {
                    LocalDate qd = queueDateFromStart(appointment.getStartTime());
                    removeFromQueue(appointment.getPatient().getId(), appointment.getDoctor().getId(), qd);
                    removedCount++;
                    if (affectedDoctorDayKeys != null) {
                        affectedDoctorDayKeys.add(appointment.getDoctor().getId() + "|" + qd);
                    }
                    System.out.println("DEBUG Redis: Удален просроченный appointment " + appointmentId +
                            " из очереди к врачу " + appointment.getDoctor().getId() + " день " + qd);
                }
            } else {
                activeAppointmentIds.add(appointmentId);
            }
        }

        if (removedCount > 0) {
            session.setAppointmentIds(activeAppointmentIds);
            saveSession(sessionId, session);
        }

        return removedCount;
    }

    /**
     * Пересчитывает ZSET очереди врача на сегодня ({@link LocalDate#now()} в системной зоне JVM).
     *
     * @param doctorId идентификатор врача
     * @see #recalculateQueueForDoctor(Long, LocalDate)
     */
    public void recalculateQueueForDoctor(Long doctorId) {
        recalculateQueueForDoctor(doctorId, LocalDate.now());
    }

    /**
     * Очищает ключ очереди на дату и заново заполняет по предстоящим незавершённым приёмам врача, затем шлёт STOMP.
     *
     * @param doctorId  врач
     * @param queueDate календарный день очереди; {@code null} трактуется как сегодня
     */
    public void recalculateQueueForDoctor(Long doctorId, LocalDate queueDate) {
        recalculateQueueForDoctor(doctorId, queueDate, true);
    }

    private void recalculateQueueForDoctor(Long doctorId, LocalDate queueDate, boolean notify) {
        if (queueDate == null) {
            queueDate = LocalDate.now();
        }
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate targetDate = queueDate;

        List<Appointment> doctorAppointments = appointmentRepository.findByDoctorId(doctorId)
                .stream()
                .filter(a -> isActiveQueueAppointment(a, targetDate, now))
                .sorted((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime()))
                .collect(Collectors.toList());

        clearQueue(doctorId, queueDate);

        int position = 0;
        for (Appointment appointment : doctorAppointments) {
            addToQueueWithoutNotification(appointment.getPatient().getId(), doctorId, position, queueDate);
            position++;
        }

        if (notify) {
            notifyQueueUpdated(doctorId, queueDate);
        }

        System.out.println("DEBUG Redis: Очередь к врачу " + doctorId + " на " + queueDate + " пересчитана, "
                + doctorAppointments.size() + " пациентов");
    }

    /**
     * Пересчитывает очередь, к которой относится конкретный приём.
     *
     * @param appointment приём, чьи doctor/date могли повлиять на live-очередь
     */
    public void syncQueueForAppointment(Appointment appointment) {
        QueueScope scope = queueScopeOf(appointment);
        if (scope != null) {
            recalculateQueueForDoctor(scope.doctorId(), scope.queueDate());
        }
    }

    /**
     * Пересчитывает старую и новую очереди после изменения doctor/date/patient/status приёма.
     *
     * @param before состояние до изменения
     * @param after  состояние после сохранения
     */
    public void syncQueueForAppointmentChange(Appointment before, Appointment after) {
        Set<QueueScope> scopes = new HashSet<>();
        QueueScope beforeScope = queueScopeOf(before);
        QueueScope afterScope = queueScopeOf(after);
        if (beforeScope != null) {
            scopes.add(beforeScope);
        }
        if (afterScope != null) {
            scopes.add(afterScope);
        }
        scopes.forEach(scope -> recalculateQueueForDoctor(scope.doctorId(), scope.queueDate()));
    }

    // ==================== QUEUE OPERATIONS ====================

    /**
     * Добавляет пациента в очередь на сегодняшний день с указанным score.
     *
     * @param patientId идентификатор пациента
     * @param doctorId  врач
     * @param position  позиция (score в ZSET)
     * @see #addToQueue(Long, Long, Integer, LocalDate)
     */
    public void addToQueue(Long patientId, Long doctorId, Integer position) {
        addToQueue(patientId, doctorId, position, LocalDate.now());
    }

    /**
     * Добавляет member {@code patient:{id}} в ZSET с уведомлением подписчиков топика очереди.
     *
     * @param patientId идентификатор пациента
     * @param doctorId  врач
     * @param position  score (позиция)
     * @param queueDate день очереди; {@code null} — сегодня
     */
    public void addToQueue(Long patientId, Long doctorId, Integer position, LocalDate queueDate) {
        if (queueDate == null) {
            queueDate = LocalDate.now();
        }
        String queueKey = getQueueKey(doctorId, queueDate);
        String patientKey = "patient:" + patientId;

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(queueKey, patientKey, position);

        notifyQueueUpdated(doctorId, queueDate);
    }

    /**
     * Добавление в ZSET без вызова {@link #notifyQueueUpdated(Long, LocalDate)} (для пакетного пересчёта).
     *
     * @param patientId идентификатор пациента
     * @param doctorId  врач
     * @param position  score
     * @param queueDate день очереди
     */
    private void addToQueueWithoutNotification(Long patientId, Long doctorId, Integer position, LocalDate queueDate) {
        if (queueDate == null) {
            queueDate = LocalDate.now();
        }
        String queueKey = getQueueKey(doctorId, queueDate);
        String patientKey = "patient:" + patientId;

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(queueKey, patientKey, position);
    }

    /**
     * Удаляет пациента из очереди врача на сегодня.
     *
     * @param patientId идентификатор пациента
     * @param doctorId  врач
     * @return {@code true}, если Lua-скрипт вернул ненулевой результат
     * @see #removeFromQueue(Long, Long, LocalDate)
     */
    public boolean removeFromQueue(Long patientId, Long doctorId) {
        return removeFromQueue(patientId, doctorId, LocalDate.now());
    }

    /**
     * Удаляет пациента из ZSET на дату и сдвигает score остальных через {@link DefaultRedisScript}.
     *
     * @param patientId идентификатор пациента
     * @param doctorId  врач
     * @param queueDate день; {@code null} — сегодня
     * @return {@code true}, если удаление выполнено
     */
    public boolean removeFromQueue(Long patientId, Long doctorId, LocalDate queueDate) {
        if (queueDate == null) {
            queueDate = LocalDate.now();
        }
        String queueKey = getQueueKey(doctorId, queueDate);
        String patientKey = "patient:" + patientId;

        Long result = redisTemplate.execute(
            removeAndShiftScript,
            List.of(queueKey),
            patientKey
        );

        if (result != null && result > 0) {
            notifyQueueUpdated(doctorId, queueDate);
            return true;
        }

        return false;
    }

    /**
     * Возвращает упорядоченную очередь врача на сегодня.
     *
     * @param doctorId врач
     * @return список {@link QueueEntryDto}
     * @see #getQueueByDoctor(Long, LocalDate)
     */
    public List<QueueEntryDto> getQueueByDoctor(Long doctorId) {
        return getQueueByDoctor(doctorId, LocalDate.now());
    }

    /**
     * Читает ZSET и для каждого пациента пытается найти связанный приём на этот день.
     *
     * @param doctorId  врач
     * @param queueDate день; {@code null} — сегодня
     * @return список позиций и идентификаторов
     */
    public List<QueueEntryDto> getQueueByDoctor(Long doctorId, LocalDate queueDate) {
        final LocalDate day = queueDate == null ? LocalDate.now() : queueDate;
        String queueKey = getQueueKey(doctorId, day);
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        Set<ZSetOperations.TypedTuple<String>> members = zSetOps.rangeWithScores(queueKey, 0, -1);

        if (members == null) {
            return List.of();
        }

        return members.stream()
                .map(tuple -> {
                    String patientKey = tuple.getValue();
                    Long patientId = extractPatientId(patientKey);
                    Integer position = tuple.getScore() != null ? tuple.getScore().intValue() : 0;

                    Appointment appointment = findAppointment(patientId, doctorId, day);

                    QueueEntryDto dto = new QueueEntryDto(
                            null,
                            doctorId,
                            appointment != null ? appointment.getId() : null,
                            patientId,
                            position,
                            OffsetDateTime.now()
                    );
                    dto.setPatientFullName(resolvePatientFullName(patientId));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private String resolvePatientFullName(Long patientId) {
        if (patientId == null) {
            return null;
        }
        return patientRepository.findById(patientId)
                .map(this::formatPatientFullName)
                .orElse(null);
    }

    private String formatPatientFullName(Patient patient) {
        if (patient == null || patient.getUser() == null) {
            return null;
        }
        User user = patient.getUser();
        String name = String.join(" ",
                nullToEmpty(user.getLastName()),
                nullToEmpty(user.getFirstName()),
                nullToEmpty(user.getMiddleName())).trim();
        return name.isEmpty() ? null : name;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Позиция пациента в очереди врача на сегодня (score в ZSET, целая часть).
     *
     * @param patientId пациент
     * @param doctorId  врач
     * @return позиция или {@code null}, если member отсутствует
     * @see #getPatientPosition(Long, Long, LocalDate)
     */
    public Integer getPatientPosition(Long patientId, Long doctorId) {
        return getPatientPosition(patientId, doctorId, LocalDate.now());
    }

    /**
     * Возвращает score member {@code patient:{patientId}} в ZSET очереди на дату.
     *
     * @param patientId идентификатор пациента
     * @param doctorId  врач
     * @param queueDate день; {@code null} — сегодня
     * @return целая часть score или {@code null}
     */
    public Integer getPatientPosition(Long patientId, Long doctorId, LocalDate queueDate) {
        if (queueDate == null) {
            queueDate = LocalDate.now();
        }
        String queueKey = getQueueKey(doctorId, queueDate);
        String patientKey = "patient:" + patientId;

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        Double score = zSetOps.score(queueKey, patientKey);

        return score != null ? score.intValue() : null;
    }

    /**
     * Проверяет «следующий ли пациент» на сегодня.
     *
     * @param patientId пациент
     * @param doctorId  врач
     * @return результат {@link #isPatientNextInQueue(Long, Long, LocalDate)}
     */
    public boolean isPatientNextInQueue(Long patientId, Long doctorId) {
        return isPatientNextInQueue(patientId, doctorId, LocalDate.now());
    }

    /**
     * {@code true}, если позиция 0 или нет других member с меньшим score перед пациентом.
     *
     * @param patientId пациент
     * @param doctorId  врач
     * @param queueDate день очереди; {@code null} при внутреннем вызове обрабатывается как сегодня
     * @return признак «следующий в очереди»
     */
    public boolean isPatientNextInQueue(Long patientId, Long doctorId, LocalDate queueDate) {
        Integer position = getPatientPosition(patientId, doctorId, queueDate);
        if (position == null) {
            return false;
        }

        if (position == 0) {
            return true;
        }

        String queueKey = getQueueKey(doctorId, queueDate == null ? LocalDate.now() : queueDate);
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        Long count = zSetOps.count(queueKey, 0, position - 1);

        return count == null || count == 0;
    }

    /**
     * Число member в ZSET очереди врача на сегодня ({@link org.springframework.data.redis.core.ZSetOperations#zCard}).
     *
     * @param doctorId врач
     * @return размер или {@code null}, если Redis вернул {@code null}
     * @see #getQueueSize(Long, LocalDate)
     */
    public Long getQueueSize(Long doctorId) {
        return getQueueSize(doctorId, LocalDate.now());
    }

    /**
     * Размер очереди на указанную дату.
     *
     * @param doctorId  врач
     * @param queueDate день; {@code null} — сегодня
     * @return количество записей в ZSET
     */
    public Long getQueueSize(Long doctorId, LocalDate queueDate) {
        if (queueDate == null) {
            queueDate = LocalDate.now();
        }
        String queueKey = getQueueKey(doctorId, queueDate);
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        return zSetOps.zCard(queueKey);
    }

    /**
     * Удаляет ключ очереди врача на сегодня.
     *
     * @param doctorId врач
     * @see #clearQueue(Long, LocalDate)
     */
    public void clearQueue(Long doctorId) {
        clearQueue(doctorId, LocalDate.now());
    }

    /**
     * Полностью удаляет ZSET очереди на календарный день без уведомлений.
     *
     * @param doctorId  врач
     * @param queueDate день; {@code null} — сегодня
     */
    public void clearQueue(Long doctorId, LocalDate queueDate) {
        if (queueDate == null) {
            queueDate = LocalDate.now();
        }
        String queueKey = getQueueKey(doctorId, queueDate);
        redisTemplate.delete(queueKey);
    }

    /**
     * Очищает очередь на сегодня и отправляет обновление в STOMP.
     *
     * @param doctorId врач
     * @see #clearQueueWithNotification(Long, LocalDate)
     */
    public void clearQueueWithNotification(Long doctorId) {
        clearQueueWithNotification(doctorId, LocalDate.now());
    }

    /**
     * Удаляет ключ очереди и вызывает {@link #notifyQueueUpdated(Long, LocalDate)}.
     *
     * @param doctorId  врач
     * @param queueDate день; {@code null} — сегодня
     */
    public void clearQueueWithNotification(Long doctorId, LocalDate queueDate) {
        clearQueue(doctorId, queueDate);
        notifyQueueUpdated(doctorId, queueDate);
    }

    /**
     * Рассылает актуальное состояние очереди врача на сегодня в топик {@code /topic/queue/doctor/{id}}.
     *
     * @param doctorId врач
     * @see #notifyQueueUpdated(Long, LocalDate)
     */
    public void notifyQueueUpdated(Long doctorId) {
        notifyQueueUpdated(doctorId, LocalDate.now());
    }

    /**
     * Отправляет {@link QueueUpdateEvent} с полным списком {@link QueueEntryDto} подписчикам.
     *
     * @param doctorId  врач
     * @param queueDate день; {@code null} — сегодня
     */
    public void notifyQueueUpdated(Long doctorId, LocalDate queueDate) {
        if (queueDate == null) {
            queueDate = LocalDate.now();
        }
        List<QueueEntryDto> queue = getQueueByDoctor(doctorId, queueDate);

        messagingTemplate.convertAndSend(
            "/topic/queue/doctor/" + doctorId,
            new QueueUpdateEvent(doctorId, queue)
        );
    }

    /**
     * Персональное STOMP-сообщение пользователю ({@code /queue/user}) с обновлённым списком его очередей.
     *
     * @param email        principal/email пользователя
     * @param queueEntries данные для {@link QueueInitResponse}
     */
    public void notifyUserQueueUpdate(String email, List<QueueEntryDto> queueEntries) {
        messagingTemplate.convertAndSendToUser(
            email,
            "/queue/user",
            new QueueInitResponse(true, "Очередь обновлена", queueEntries)
        );
    }

    /**
     * По активным приёмам пациента собирает его позиции в очередях разных врачей (через {@link #getQueueByDoctor}).
     *
     * @param patientId идентификатор пациента
     * @return непустые {@link QueueEntryDto} для каждой релевантной очереди
     */
    public List<QueueEntryDto> getQueuesByPatient(Long patientId) {
        return getQueuesByPatient(patientId, QueueScopeMode.ALL);
    }

    /**
     * По активным приёмам пациента собирает его позиции в очередях с учётом режима фильтрации.
     *
     * @param patientId идентификатор пациента
     * @param mode      {@code TODAY} — только сегодня, {@code ALL} — все будущие активные приёмы
     * @return непустые {@link QueueEntryDto} для каждой релевантной очереди
     */
    public List<QueueEntryDto> getQueuesByPatient(Long patientId, QueueScopeMode mode) {
        List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate today = LocalDate.now();
        QueueScopeMode effectiveMode = mode == null ? QueueScopeMode.ALL : mode;
        Map<Long, Appointment> nearestByDoctor = appointments.stream()
                .filter(a -> a.getDoctor() != null)
                .filter(a -> isActiveQueueAppointment(a, queueDateFromStart(a.getStartTime()), now))
                .filter(a -> effectiveMode == QueueScopeMode.ALL || today.equals(queueDateFromStart(a.getStartTime())))
                .collect(Collectors.toMap(
                        a -> a.getDoctor().getId(),
                        a -> a,
                        (a1, a2) -> a1.getStartTime().isBefore(a2.getStartTime()) ? a1 : a2
                ));

        return nearestByDoctor.values().stream()
                .sorted(Comparator.comparing(Appointment::getStartTime))
                .map(appointment -> {
                    Long doctorId = appointment.getDoctor().getId();
                    LocalDate qd = queueDateFromStart(appointment.getStartTime());
                    Integer position = getPatientPosition(patientId, doctorId, qd);
                    if (position == null) {
                        recalculateQueueForDoctor(doctorId, qd, false);
                        position = getPatientPosition(patientId, doctorId, qd);
                    }
                    if (position == null) {
                        return null;
                    }
                    QueueEntryDto dto = getQueueByDoctor(doctorId, qd).stream()
                            .filter(e -> patientId.equals(e.getPatientId()))
                            .findFirst()
                            .orElse(null);
                    if (dto != null) {
                        dto.setAppointmentId(appointment.getId());
                    }
                    return dto;
                })
                .filter(e -> e != null)
                .collect(Collectors.toList());
    }

    /**
     * Для каждой активной сессии и каждого незавершённого приёма вызывает {@link #recalculateQueueForDoctor(Long, LocalDate)} не более одного раза на пару (врач, день).
     */
    public void recalculateQueuesForAllActiveSessionPatients() {
        Set<String> done = new HashSet<>();
        for (WebSocketSessionData s : getAllActiveSessions()) {
            if (s.getPatientId() == null) {
                continue;
            }
            for (Appointment a : appointmentRepository.findByPatientId(s.getPatientId())) {
                if (a.getDoctor() == null) {
                    continue;
                }
                if (isTerminalStatus(a.getStatus())) {
                    continue;
                }
                LocalDate d = queueDateFromStart(a.getStartTime());
                String key = a.getDoctor().getId() + ":" + d;
                if (done.add(key)) {
                    recalculateQueueForDoctor(a.getDoctor().getId(), d);
                }
            }
        }
    }

    /**
     * Устаревший алиас на {@link #buildQueueForToday(Long)}.
     *
     * @param patientId идентификатор пациента
     * @deprecated используйте {@link #buildQueueForToday(Long)}
     */
    @Deprecated
    public void buildQueueFromAppointments(Long patientId) {
        buildQueueForToday(patientId);
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Ключ Redis для ZSET очереди врача на дату.
     *
     * @param doctorId  врач
     * @param queueDate день; {@code null} — сегодня
     * @return строка ключа
     */
    private String getQueueKey(Long doctorId, LocalDate queueDate) {
        if (queueDate == null) {
            queueDate = LocalDate.now();
        }
        return QUEUE_KEY_PREFIX + doctorId + ":" + queueDate;
    }

    /**
     * Календарный день очереди по времени начала приёма в системной зоне JVM.
     *
     * @param startTime время начала или {@code null}
     * @return дата или сегодня, если {@code startTime == null}
     */
    private static LocalDate queueDateFromStart(OffsetDateTime startTime) {
        if (startTime == null) {
            return LocalDate.now();
        }
        return startTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
    }

    private boolean isActiveQueueAppointment(Appointment appointment, LocalDate queueDate, OffsetDateTime now) {
        if (appointment == null
                || appointment.getPatient() == null
                || appointment.getDoctor() == null
                || appointment.getStartTime() == null) {
            return false;
        }
        if (isTerminalStatus(appointment.getStatus())) {
            return false;
        }
        LocalDate appointmentDate = queueDateFromStart(appointment.getStartTime());
        if (queueDate != null && !queueDate.equals(appointmentDate)) {
            return false;
        }
        return appointment.getStartTime().isAfter(now);
    }

    private boolean isTerminalStatus(String status) {
        return status != null && TERMINAL_STATUSES.contains(status);
    }

    private QueueScope queueScopeOf(Appointment appointment) {
        if (appointment == null || appointment.getDoctor() == null || appointment.getStartTime() == null) {
            return null;
        }
        return new QueueScope(appointment.getDoctor().getId(), queueDateFromStart(appointment.getStartTime()));
    }

    /**
     * Парсит {@code patient:123} в Long.
     *
     * @param patientKey member ZSET
     * @return id или {@code null}
     */
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

    /**
     * Первый активный приём пациента к врачу в пределах календарного дня (системная зона).
     *
     * @param patientId идентификатор пациента
     * @param doctorId  врач
     * @param queueDate день
     * @return {@link Appointment} или {@code null}
     */
    private Appointment findAppointment(Long patientId, Long doctorId, LocalDate queueDate) {
        if (patientId == null || doctorId == null || queueDate == null) {
            return null;
        }
        OffsetDateTime startOfDay = queueDate.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime endOfDay = queueDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();

        return appointmentRepository
                .findByPatientId(patientId)
                .stream()
                .filter(a -> a.getDoctor() != null && doctorId.equals(a.getDoctor().getId()))
                .filter(a -> a.getStartTime() != null)
                .filter(a -> !a.getStartTime().isBefore(startOfDay) && a.getStartTime().isBefore(endOfDay))
                .filter(a -> !isTerminalStatus(a.getStatus()))
                .sorted(Comparator.comparing(Appointment::getStartTime))
                .findFirst()
                .orElse(null);
    }

    private record QueueScope(Long doctorId, LocalDate queueDate) {
    }

    // ==================== DTO CLASSES ====================

    /**
     * Полезная нагрузка STOMP при изменении очереди врача (топик {@code /topic/queue/doctor/…}).
     */
    public static class QueueUpdateEvent {
        private Long doctorId;
        private List<QueueEntryDto> queue;

        /**
         * @param doctorId идентификатор врача
         * @param queue    актуальный снимок очереди
         */
        public QueueUpdateEvent(Long doctorId, List<QueueEntryDto> queue) {
            this.doctorId = doctorId;
            this.queue = queue;
        }

        /**
         * @return идентификатор врача
         */
        public Long getDoctorId() {
            return doctorId;
        }

        /**
         * @param doctorId идентификатор врача
         */
        public void setDoctorId(Long doctorId) {
            this.doctorId = doctorId;
        }

        /**
         * @return список {@link QueueEntryDto}
         */
        public List<QueueEntryDto> getQueue() {
            return queue;
        }

        /**
         * @param queue список позиций очереди
         */
        public void setQueue(List<QueueEntryDto> queue) {
            this.queue = queue;
        }
    }

    /**
     * Ответ персональной очереди пользователю при инициализации или обновлении ({@link #notifyUserQueueUpdate}).
     */
    public static class QueueInitResponse {
        private boolean success;
        private String message;
        private List<QueueEntryDto> data;

        /**
         * @param success признак успеха операции
         * @param message текст для клиента
         * @param data    список записей очереди пациента
         */
        public QueueInitResponse(boolean success, String message, List<QueueEntryDto> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        /**
         * @return {@code true}, если операция успешна
         */
        public boolean isSuccess() { return success; }

        /**
         * @param success признак успеха
         */
        public void setSuccess(boolean success) { this.success = success; }

        /**
         * @return сообщение для отображения
         */
        public String getMessage() { return message; }

        /**
         * @param message сообщение
         */
        public void setMessage(String message) { this.message = message; }

        /**
         * @return данные очереди
         */
        public List<QueueEntryDto> getData() { return data; }

        /**
         * @param data список {@link QueueEntryDto}
         */
        public void setData(List<QueueEntryDto> data) { this.data = data; }
    }
}
