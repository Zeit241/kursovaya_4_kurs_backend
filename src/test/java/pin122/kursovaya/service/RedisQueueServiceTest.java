package pin122.kursovaya.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.dto.WebSocketSessionData;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.PatientRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisQueueService - тесты очереди Redis")
class RedisQueueServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private DefaultRedisScript<Long> removeAndShiftScript;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private RedisQueueService redisQueueService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        redisQueueService = new RedisQueueService(
                redisTemplate, removeAndShiftScript, appointmentRepository, patientRepository, messagingTemplate);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("generateSessionId возвращает валидный UUID")
    void generateSessionId_returnsValidUuid() {
        String sessionId = redisQueueService.generateSessionId();

        assertNotNull(sessionId);
        assertDoesNotThrow(() -> UUID.fromString(sessionId));
    }

    @Test
    @DisplayName("saveSession сохраняет JSON и регистрирует сессию в индексах")
    void saveSession_storesDataAndRegistersInSets() throws Exception {
        WebSocketSessionData sessionData = new WebSocketSessionData(
                "sess-1", 1L, 10L, "patient@test.com", List.of(100L), LocalDateTime.now());

        redisQueueService.saveSession("sess-1", sessionData);

        verify(valueOperations).set(eq("ws:session:sess-1"), anyString());
        verify(setOperations).add("ws:sessions:active", "sess-1");
        verify(setOperations).add("patient:sessions:10", "sess-1");
    }

    @Test
    @DisplayName("getSession возвращает данные существующей сессии")
    void getSession_existingSession_returnsData() throws Exception {
        WebSocketSessionData expected = new WebSocketSessionData(
                "sess-2", 2L, 20L, "user@test.com", List.of(200L), LocalDateTime.now());
        String json = objectMapper.writeValueAsString(expected);
        when(valueOperations.get("ws:session:sess-2")).thenReturn(json);

        WebSocketSessionData result = redisQueueService.getSession("sess-2");

        assertNotNull(result);
        assertEquals(20L, result.getPatientId());
        assertEquals("user@test.com", result.getEmail());
    }

    @Test
    @DisplayName("getSession возвращает null для отсутствующей сессии")
    void getSession_missingSession_returnsNull() {
        when(valueOperations.get("ws:session:missing")).thenReturn(null);

        WebSocketSessionData result = redisQueueService.getSession("missing");

        assertNull(result);
    }

    @Test
    @DisplayName("deleteSession удаляет ключи и индексы сессии")
    void deleteSession_removesSessionFromRedis() throws Exception {
        WebSocketSessionData sessionData = new WebSocketSessionData(
                "sess-del", 1L, 5L, "del@test.com", List.of(), LocalDateTime.now());
        String json = objectMapper.writeValueAsString(sessionData);
        when(valueOperations.get("ws:session:sess-del")).thenReturn(json);
        when(setOperations.size("patient:sessions:5")).thenReturn(0L);
        when(appointmentRepository.findByPatientId(5L)).thenReturn(Collections.emptyList());

        redisQueueService.deleteSession("sess-del");

        verify(redisTemplate).delete("ws:session:sess-del");
        verify(setOperations).remove("patient:sessions:5", "sess-del");
        verify(setOperations).remove("ws:sessions:active", "sess-del");
    }

    @Test
    @DisplayName("getAllActiveSessions возвращает список активных сессий")
    void getAllActiveSessions_returnsActiveSessions() throws Exception {
        WebSocketSessionData session = new WebSocketSessionData(
                "s1", 1L, 1L, "a@test.com", List.of(), LocalDateTime.now());
        when(setOperations.members("ws:sessions:active")).thenReturn(Set.of("s1"));
        when(valueOperations.get("ws:session:s1")).thenReturn(objectMapper.writeValueAsString(session));

        List<WebSocketSessionData> result = redisQueueService.getAllActiveSessions();

        assertEquals(1, result.size());
        assertEquals("a@test.com", result.get(0).getEmail());
    }

    @Test
    @DisplayName("getAllActiveSessions возвращает пустой список без активных сессий")
    void getAllActiveSessions_noSessions_returnsEmptyList() {
        when(setOperations.members("ws:sessions:active")).thenReturn(Collections.emptySet());

        List<WebSocketSessionData> result = redisQueueService.getAllActiveSessions();

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("hasActiveSessions возвращает true при наличии сессий пациента")
    void hasActiveSessions_whenSessionsExist_returnsTrue() {
        when(setOperations.size("patient:sessions:42")).thenReturn(2L);

        assertTrue(redisQueueService.hasActiveSessions(42L));
    }

    @Test
    @DisplayName("addToQueue добавляет пациента в ZSET и отправляет уведомление")
    void addToQueue_addsMemberAndNotifies() {
        LocalDate today = LocalDate.now();
        String queueKey = "queue:doctor:7:" + today;

        redisQueueService.addToQueue(15L, 7L, 2, today);

        verify(zSetOperations).add(queueKey, "patient:15", 2.0);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/queue/doctor/7"), any(RedisQueueService.QueueUpdateEvent.class));
    }

    @Test
    @DisplayName("removeFromQueue возвращает true при успешном удалении")
    void removeFromQueue_success_returnsTrue() {
        LocalDate today = LocalDate.now();
        String queueKey = "queue:doctor:3:" + today;
        when(redisTemplate.execute(eq(removeAndShiftScript), eq(List.of(queueKey)), eq("patient:8")))
                .thenReturn(1L);
        when(zSetOperations.rangeWithScores(queueKey, 0, -1)).thenReturn(Collections.emptySet());

        boolean result = redisQueueService.removeFromQueue(8L, 3L, today);

        assertTrue(result);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/queue/doctor/3"), any(RedisQueueService.QueueUpdateEvent.class));
    }

    @Test
    @DisplayName("removeFromQueue возвращает false если пациент не найден в очереди")
    void removeFromQueue_notFound_returnsFalse() {
        when(redisTemplate.execute(eq(removeAndShiftScript), anyList(), anyString())).thenReturn(0L);

        boolean result = redisQueueService.removeFromQueue(99L, 1L, LocalDate.now());

        assertFalse(result);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("getPatientPosition возвращает score из ZSET")
    void getPatientPosition_returnsScore() {
        LocalDate today = LocalDate.now();
        String queueKey = "queue:doctor:5:" + today;
        when(zSetOperations.score(queueKey, "patient:12")).thenReturn(3.0);

        Integer position = redisQueueService.getPatientPosition(12L, 5L, today);

        assertEquals(3, position);
    }

    @Test
    @DisplayName("getQueueSize возвращает размер ZSET")
    void getQueueSize_returnsZCardResult() {
        LocalDate today = LocalDate.now();
        String queueKey = "queue:doctor:4:" + today;
        when(zSetOperations.zCard(queueKey)).thenReturn(5L);

        Long size = redisQueueService.getQueueSize(4L, today);

        assertEquals(5L, size);
    }

    @Test
    @DisplayName("isPatientNextInQueue возвращает true для позиции 0")
    void isPatientNextInQueue_positionZero_returnsTrue() {
        LocalDate today = LocalDate.now();
        String queueKey = "queue:doctor:6:" + today;
        when(zSetOperations.score(queueKey, "patient:1")).thenReturn(0.0);

        assertTrue(redisQueueService.isPatientNextInQueue(1L, 6L, today));
    }

    @Test
    @DisplayName("notifyUserQueueUpdate отправляет персональное STOMP-сообщение")
    void notifyUserQueueUpdate_sendsPersonalMessage() {
        List<QueueEntryDto> entries = List.of(
                new QueueEntryDto(null, 1L, 10L, 5L, 0, OffsetDateTime.now()));

        redisQueueService.notifyUserQueueUpdate("user@test.com", entries);

        verify(messagingTemplate).convertAndSendToUser(
                eq("user@test.com"), eq("/queue/user"), any(RedisQueueService.QueueInitResponse.class));
    }

    @Test
    @DisplayName("recalculateQueueForDoctor включает активные статусы и исключает no_show")
    void recalculateQueueForDoctor_excludesNoShowFromRebuiltQueue() {
        LocalDate today = LocalDate.now();
        String queueKey = "queue:doctor:9:" + today;
        Appointment waiting = appointment(101L, 201L, "waiting", OffsetDateTime.now().plusHours(1));
        Appointment noShow = appointment(102L, 202L, "no_show", OffsetDateTime.now().plusHours(2));
        when(appointmentRepository.findByDoctorId(9L)).thenReturn(List.of(waiting, noShow));
        when(zSetOperations.rangeWithScores(queueKey, 0, -1)).thenReturn(Collections.emptySet());

        redisQueueService.recalculateQueueForDoctor(9L, today);

        verify(redisTemplate).delete(queueKey);
        verify(zSetOperations).add(queueKey, "patient:201", 0.0);
        verify(zSetOperations, never()).add(queueKey, "patient:202", 1.0);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/queue/doctor/9"), any(RedisQueueService.QueueUpdateEvent.class));
    }

    private static Appointment appointment(Long appointmentId, Long patientId, String status, OffsetDateTime startTime) {
        Doctor doctor = new Doctor();
        doctor.setId(9L);
        Patient patient = new Patient();
        patient.setId(patientId);
        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setStatus(status);
        appointment.setStartTime(startTime);
        return appointment;
    }
}
