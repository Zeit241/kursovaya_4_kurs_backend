package pin122.kursovaya.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.dto.WebSocketSessionData;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.User;
import pin122.kursovaya.service.RedisQueueService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты Redis-очереди и WebSocket-сессий через Testcontainers Redis.
 */
@DisplayName("QueueRedisIT")
class QueueRedisIT extends AbstractIntegrationIT {

    private static final String PASSWORD = "QueuePass123!";

    @Autowired
    private RedisQueueService redisQueueService;

    @Test
    @DisplayName("addToQueue сохраняет позицию пациента в ZSET")
    void addToQueue_setsPatientPosition() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        LocalDate day = today();

        redisQueueService.addToQueue(requirePatientId(patientUser), doctor.getId(), 2, day);

        Integer position = redisQueueService.getPatientPosition(
                requirePatientId(patientUser), doctor.getId(), day);
        assertEquals(2, position);
    }

    @Test
    @DisplayName("removeFromQueue удаляет пациента и сдвигает score остальных")
    void removeFromQueue_shiftsRemainingPatients() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User p1 = createPatientUser(uniqueEmail("p1"), PASSWORD);
        User p2 = createPatientUser(uniqueEmail("p2"), PASSWORD);
        User p3 = createPatientUser(uniqueEmail("p3"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        LocalDate day = today();

        redisQueueService.addToQueue(requirePatientId(p1), doctor.getId(), 0, day);
        redisQueueService.addToQueue(requirePatientId(p2), doctor.getId(), 1, day);
        redisQueueService.addToQueue(requirePatientId(p3), doctor.getId(), 2, day);

        assertTrue(redisQueueService.removeFromQueue(requirePatientId(p2), doctor.getId(), day));
        assertNull(redisQueueService.getPatientPosition(requirePatientId(p2), doctor.getId(), day));
        assertEquals(0, redisQueueService.getPatientPosition(requirePatientId(p1), doctor.getId(), day));
        assertEquals(1, redisQueueService.getPatientPosition(requirePatientId(p3), doctor.getId(), day));
    }

    @Test
    @DisplayName("getQueueByDoctor возвращает упорядоченный список записей")
    void getQueueByDoctor_returnsOrderedEntries() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User p1 = createPatientUser(uniqueEmail("p1"), PASSWORD);
        User p2 = createPatientUser(uniqueEmail("p2"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        LocalDate day = today();

        redisQueueService.addToQueue(requirePatientId(p1), doctor.getId(), 0, day);
        redisQueueService.addToQueue(requirePatientId(p2), doctor.getId(), 1, day);

        List<QueueEntryDto> queue = redisQueueService.getQueueByDoctor(doctor.getId(), day);

        assertEquals(2, queue.size());
        assertEquals(0, queue.get(0).getPosition());
        assertEquals(1, queue.get(1).getPosition());
    }

    @Test
    @DisplayName("getQueueSize возвращает количество пациентов в очереди")
    void getQueueSize_countsMembers() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User p1 = createPatientUser(uniqueEmail("p1"), PASSWORD);
        User p2 = createPatientUser(uniqueEmail("p2"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        LocalDate day = today();

        redisQueueService.addToQueue(requirePatientId(p1), doctor.getId(), 0, day);
        redisQueueService.addToQueue(requirePatientId(p2), doctor.getId(), 1, day);

        assertEquals(2L, redisQueueService.getQueueSize(doctor.getId(), day));
    }

    @Test
    @DisplayName("clearQueue удаляет ключ очереди в Redis")
    void clearQueue_removesQueueKey() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        LocalDate day = today();

        redisQueueService.addToQueue(requirePatientId(patientUser), doctor.getId(), 0, day);
        assertEquals(1L, redisQueueService.getQueueSize(doctor.getId(), day));

        redisQueueService.clearQueue(doctor.getId(), day);

        assertEquals(0L, redisQueueService.getQueueSize(doctor.getId(), day));
    }

    @Test
    @DisplayName("saveSession и getSession сохраняют JSON-сессию в Redis")
    void saveSession_roundTripsSessionData() {
        WebSocketSessionData data = new WebSocketSessionData(
                "session-1",
                10L,
                20L,
                "ws@test.com",
                List.of(1L, 2L),
                LocalDateTime.now()
        );

        redisQueueService.saveSession("session-1", data);
        WebSocketSessionData loaded = redisQueueService.getSession("session-1");

        assertNotNull(loaded);
        assertEquals(20L, loaded.getPatientId());
        assertEquals("ws@test.com", loaded.getEmail());
        assertEquals(2, loaded.getAppointmentIds().size());
    }

    @Test
    @DisplayName("deleteSession удаляет сессию из активных множеств")
    void deleteSession_removesFromActiveSet() {
        WebSocketSessionData data = new WebSocketSessionData(
                "session-del",
                11L,
                21L,
                "del@test.com",
                List.of(),
                LocalDateTime.now()
        );
        redisQueueService.saveSession("session-del", data);
        assertTrue(redisQueueService.hasActiveSessions(21L));

        redisQueueService.deleteSession("session-del");

        assertFalse(redisQueueService.hasActiveSessions(21L));
        assertNull(redisQueueService.getSession("session-del"));
    }

    @Test
    @DisplayName("hasActiveSessions возвращает true при наличии сессий пациента")
    void hasActiveSessions_reflectsPatientSessions() {
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        Long patientId = requirePatientId(patientUser);

        assertFalse(redisQueueService.hasActiveSessions(patientId));

        WebSocketSessionData data = new WebSocketSessionData(
                "session-active",
                patientUser.getId(),
                patientId,
                patientUser.getEmail(),
                List.of(),
                LocalDateTime.now()
        );
        redisQueueService.saveSession("session-active", data);

        assertTrue(redisQueueService.hasActiveSessions(patientId));
        Set<String> sessions = redisQueueService.getPatientSessions(patientId);
        assertNotNull(sessions);
        assertTrue(sessions.contains("session-active"));
    }

    @Test
    @DisplayName("isPatientNextInQueue true для пациента на позиции 0")
    void isPatientNextInQueue_firstPosition() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        LocalDate day = today();

        redisQueueService.addToQueue(requirePatientId(patientUser), doctor.getId(), 0, day);

        assertTrue(redisQueueService.isPatientNextInQueue(
                requirePatientId(patientUser), doctor.getId(), day));
    }

    @Test
    @DisplayName("recalculateQueueForDoctor пересобирает очередь из appointments")
    void recalculateQueueForDoctor_rebuildsFromDatabase() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User p1 = createPatientUser(uniqueEmail("p1"), PASSWORD);
        User p2 = createPatientUser(uniqueEmail("p2"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        OffsetDateTime t1 = futureSlotTime(3);
        OffsetDateTime t2 = futureSlotTime(4);
        LocalDate day = t1.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();

        var a1 = createSlot(doctor, t1, "scheduled");
        a1.setPatient(p1.getPatient());
        appointmentRepository.save(a1);
        var a2 = createSlot(doctor, t2, "scheduled");
        a2.setPatient(p2.getPatient());
        appointmentRepository.save(a2);

        redisQueueService.recalculateQueueForDoctor(doctor.getId(), day);

        assertEquals(2L, redisQueueService.getQueueSize(doctor.getId(), day));
        assertEquals(0, redisQueueService.getPatientPosition(requirePatientId(p1), doctor.getId(), day));
        assertEquals(1, redisQueueService.getPatientPosition(requirePatientId(p2), doctor.getId(), day));
    }
}
