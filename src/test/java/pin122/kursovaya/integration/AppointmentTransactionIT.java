package pin122.kursovaya.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pin122.kursovaya.dto.AppointmentDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.User;
import pin122.kursovaya.service.AppointmentService;
import pin122.kursovaya.service.RedisQueueService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты транзакций приёмов: запись, отмена, завершение и фильтрация.
 */
@DisplayName("AppointmentTransactionIT")
class AppointmentTransactionIT extends AbstractIntegrationIT {

    private static final String PASSWORD = "SecretPass123!";

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private RedisQueueService redisQueueService;

    @Test
    @DisplayName("bookAppointment записывает пациента на свободный слот")
    void bookAppointment_freeSlot_assignsPatient() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        Appointment slot = createSlot(doctor, futureSlotTime(2), "available");

        Optional<AppointmentDto> booked = appointmentService.bookAppointment(slot.getId(), patientUser.getId());

        assertTrue(booked.isPresent());
        assertEquals("scheduled", booked.get().getStatus());
        assertEquals(requirePatientId(patientUser), booked.get().getPatientId());

        Appointment persisted = appointmentRepository.findById(slot.getId()).orElseThrow();
        assertNotNull(persisted.getPatient());
        assertEquals("scheduled", persisted.getStatus());
    }

    @Test
    @DisplayName("bookAppointment возвращает empty для уже занятого слота")
    void bookAppointment_occupiedSlot_returnsEmpty() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        User otherPatient = createPatientUser(uniqueEmail("other"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        Appointment slot = createSlot(doctor, futureSlotTime(3), "available");
        slot.setPatient(otherPatient.getPatient());
        slot.setStatus("scheduled");
        appointmentRepository.save(slot);

        Optional<AppointmentDto> result = appointmentService.bookAppointment(slot.getId(), patientUser.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("cancelAppointment переводит приём в cancelled и удаляет из Redis-очереди")
    void cancelAppointment_removesPatientFromRedisQueue() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        LocalDate queueDay = today();
        OffsetDateTime start = queueDay.atTime(15, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime();

        Appointment slot = createSlot(doctor, start, "scheduled");
        slot.setPatient(patientUser.getPatient());
        appointmentRepository.save(slot);

        Long patientId = requirePatientId(patientUser);
        redisQueueService.addToQueue(patientId, doctor.getId(), 0, queueDay);
        assertNotNull(redisQueueService.getPatientPosition(patientId, doctor.getId(), queueDay));

        Optional<AppointmentDto> cancelled = appointmentService.cancelAppointment(slot.getId(), "Болезнь");

        assertTrue(cancelled.isPresent());
        assertEquals("cancelled", cancelled.get().getStatus());
        assertEquals("Болезнь", cancelled.get().getCancelReason());
        assertNull(redisQueueService.getPatientPosition(patientId, doctor.getId(), queueDay));
    }

    @Test
    @DisplayName("completeAppointment переводит приём в completed")
    void completeAppointment_setsCompletedStatus() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        Appointment slot = createSlot(doctor, futureSlotTime(4), "scheduled");
        slot.setPatient(patientUser.getPatient());
        appointmentRepository.save(slot);

        Optional<AppointmentDto> completed = appointmentService.completeAppointment(slot.getId());

        assertTrue(completed.isPresent());
        assertEquals("completed", completed.get().getStatus());
    }

    @Test
    @DisplayName("updateAppointmentStatus не меняет запись при том же статусе")
    void updateAppointmentStatus_sameStatus_isIdempotent() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        Appointment slot = createSlot(doctor, futureSlotTime(5), "scheduled");

        Optional<AppointmentDto> result = appointmentService.updateAppointmentStatus(slot.getId(), "scheduled");

        assertTrue(result.isPresent());
        assertEquals("scheduled", result.get().getStatus());
        assertEquals(slot.getId(), result.get().getId());
    }

    @Test
    @DisplayName("deleteAppointment удаляет запись из PostgreSQL")
    void deleteAppointment_removesFromDatabase() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        Appointment slot = createSlot(doctor, futureSlotTime(6), "available");
        Long id = slot.getId();

        appointmentService.deleteAppointment(id);

        assertTrue(appointmentRepository.findById(id).isEmpty());
    }

    @Test
    @DisplayName("getAppointmentsFiltered фильтрует по врачу и статусу")
    void getAppointmentsFiltered_byDoctorAndStatus() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User otherDoctorUser = createDoctorUser(uniqueEmail("other-doctor"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        Doctor otherDoctor = requireDoctor(otherDoctorUser);

        createSlot(doctor, futureSlotTime(7), "scheduled");
        createSlot(otherDoctor, futureSlotTime(8), "scheduled");
        createSlot(doctor, futureSlotTime(9), "available");

        List<AppointmentDto> filtered = appointmentService.getAppointmentsFiltered(
                doctor.getId(), "scheduled", null);

        assertEquals(1, filtered.size());
        assertEquals("scheduled", filtered.get(0).getStatus());
        assertEquals(doctor.getId(), filtered.get(0).getDoctorId());
    }

    @Test
    @DisplayName("bookAppointment и cancelAppointment выполняются атомарно в одной транзакции")
    void bookAndCancel_inSingleTransaction_rollbacksConsistently() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        Appointment slot = createSlot(doctor, futureSlotTime(10), "available");

        Optional<AppointmentDto> booked = appointmentService.bookAppointment(slot.getId(), patientUser.getId());
        assertTrue(booked.isPresent());

        Optional<AppointmentDto> cancelled = appointmentService.cancelAppointment(slot.getId(), "test rollback");
        assertTrue(cancelled.isPresent());
        assertEquals("cancelled", cancelled.get().getStatus());

        Appointment reloaded = appointmentRepository.findById(slot.getId()).orElseThrow();
        assertEquals("cancelled", reloaded.getStatus());
        assertNotNull(reloaded.getPatient());
    }
}
