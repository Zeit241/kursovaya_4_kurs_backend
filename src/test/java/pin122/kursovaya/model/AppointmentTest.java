package pin122.kursovaya.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для модели Appointment
 */
@DisplayName("Appointment - тесты модели записи на приём")
class AppointmentTest {

    private Appointment appointment;
    private Doctor doctor;
    private Patient patient;
    private Room room;

    @BeforeEach
    void setUp() {
        // Создаём пользователя для врача
        User doctorUser = new User();
        doctorUser.setId(1L);
        doctorUser.setFirstName("Алексей");
        doctorUser.setLastName("Врачев");
        doctorUser.setEmail("doctor@clinic.com");

        // Создаём врача
        doctor = new Doctor();
        doctor.setId(1L);
        doctor.setUser(doctorUser);
        doctor.setDisplayName("Д-р Врачев");
        doctor.setExperienceYears(10);

        // Создаём пользователя для пациента
        User patientUser = new User();
        patientUser.setId(2L);
        patientUser.setFirstName("Иван");
        patientUser.setLastName("Пациентов");
        patientUser.setEmail("patient@test.com");

        // Создаём пациента
        patient = new Patient();
        patient.setId(1L);
        patient.setUser(patientUser);
        patient.setInsuranceNumber("123-456-789-04");

        // Создаём кабинет
        room = new Room();
        room.setId(1L);
        room.setCode("101");
        room.setName("Кабинет терапевта");

        // Создаём запись на приём
        appointment = new Appointment();
        appointment.setId(1L);
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setRoom(room);
        appointment.setStartTime(OffsetDateTime.now().plusDays(1));
        appointment.setEndTime(OffsetDateTime.now().plusDays(1).plusMinutes(30));
        appointment.setStatus("scheduled");
        appointment.setSource("online");
        appointment.setCreatedAt(OffsetDateTime.now());
        appointment.setUpdatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Создание записи с конструктором по умолчанию")
    void defaultConstructor_createsEmptyAppointment() {
        Appointment newAppointment = new Appointment();
        
        assertNotNull(newAppointment);
        assertNull(newAppointment.getId());
        assertNull(newAppointment.getDoctor());
        assertNull(newAppointment.getPatient());
        assertNotNull(newAppointment.getCreatedAt());
        assertNotNull(newAppointment.getUpdatedAt());
    }

    @Test
    @DisplayName("Проверка геттеров и сеттеров")
    void gettersAndSetters_workCorrectly() {
        assertEquals(1L, appointment.getId());
        assertEquals(doctor, appointment.getDoctor());
        assertEquals(patient, appointment.getPatient());
        assertEquals(room, appointment.getRoom());
        assertEquals("scheduled", appointment.getStatus());
        assertEquals("online", appointment.getSource());
        assertNotNull(appointment.getStartTime());
        assertNotNull(appointment.getEndTime());
    }

    @Test
    @DisplayName("Запись без пациента (свободный слот)")
    void appointmentWithoutPatient_isAvailable() {
        appointment.setPatient(null);
        appointment.setStatus("available");

        assertNull(appointment.getPatient());
        assertEquals("available", appointment.getStatus());
    }

    @Test
    @DisplayName("Установка диагноза")
    void setDiagnosis_updatesDiagnosis() {
        assertNull(appointment.getDiagnosis());
        
        appointment.setDiagnosis("ОРВИ");
        
        assertEquals("ОРВИ", appointment.getDiagnosis());
    }

    @Test
    @DisplayName("Установка причины отмены")
    void setCancelReason_updatesCancelReason() {
        assertNull(appointment.getCancelReason());
        
        appointment.setCancelReason("Пациент заболел");
        appointment.setStatus("cancelled");
        
        assertEquals("Пациент заболел", appointment.getCancelReason());
        assertEquals("cancelled", appointment.getStatus());
    }

    @Test
    @DisplayName("Изменение статуса записи")
    void changeStatus_updatesStatus() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  ТЕСТ: Изменение статуса записи на приём                     ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        
        System.out.println("║  Начальный статус: " + appointment.getStatus());
        assertEquals("scheduled", appointment.getStatus());
        
        appointment.setStatus("in_progress");
        System.out.println("║  Статус изменён на: " + appointment.getStatus());
        assertEquals("in_progress", appointment.getStatus());
        
        appointment.setStatus("completed");
        System.out.println("║  Финальный статус: " + appointment.getStatus());
        assertEquals("completed", appointment.getStatus());
        
        System.out.println("║  ✅ ТЕСТ ПРОЙДЕН УСПЕШНО!");
        System.out.println("║  Статусы записи корректно изменяются");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    @Test
    @DisplayName("Проверка времени записи")
    void appointmentTime_startBeforeEnd() {
        assertTrue(appointment.getStartTime().isBefore(appointment.getEndTime()),
                "Время начала должно быть раньше времени окончания");
    }

    @Test
    @DisplayName("Продолжительность записи - 30 минут")
    void appointmentDuration_is30Minutes() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        OffsetDateTime end = start.plusMinutes(30);
        
        appointment.setStartTime(start);
        appointment.setEndTime(end);
        
        long durationMinutes = java.time.Duration.between(
                appointment.getStartTime(), 
                appointment.getEndTime()
        ).toMinutes();
        
        assertEquals(30, durationMinutes);
    }

    @Test
    @DisplayName("Связь с расписанием")
    void scheduleRelation_canBeSetAndRetrieved() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        schedule.setDoctor(doctor);
        
        appointment.setSchedule(schedule);
        
        assertNotNull(appointment.getSchedule());
        assertEquals(1L, appointment.getSchedule().getId());
    }

    @Test
    @DisplayName("Установка создателя записи")
    void setCreatedBy_setsUser() {
        User admin = new User();
        admin.setId(10L);
        admin.setEmail("admin@clinic.com");
        
        appointment.setCreatedBy(admin);
        
        assertNotNull(appointment.getCreatedBy());
        assertEquals(10L, appointment.getCreatedBy().getId());
    }

    @Test
    @DisplayName("Источник записи - online/offline")
    void source_canBeOnlineOrOffline() {
        appointment.setSource("online");
        assertEquals("online", appointment.getSource());
        
        appointment.setSource("offline");
        assertEquals("offline", appointment.getSource());
    }

    @Test
    @DisplayName("Обновление updatedAt при изменении")
    void updatedAt_changesOnUpdate() {
        OffsetDateTime originalUpdatedAt = appointment.getUpdatedAt();
        OffsetDateTime newUpdatedAt = OffsetDateTime.now().plusHours(1);
        
        appointment.setUpdatedAt(newUpdatedAt);
        
        assertNotEquals(originalUpdatedAt, appointment.getUpdatedAt());
    }

    @Test
    @DisplayName("Получение информации о враче")
    void getDoctorInfo_returnsCorrectData() {
        assertNotNull(appointment.getDoctor());
        assertEquals("Д-р Врачев", appointment.getDoctor().getDisplayName());
        assertEquals(10, appointment.getDoctor().getExperienceYears());
        assertEquals("doctor@clinic.com", appointment.getDoctor().getUser().getEmail());
    }

    @Test
    @DisplayName("Получение информации о пациенте")
    void getPatientInfo_returnsCorrectData() {
        assertNotNull(appointment.getPatient());
        assertEquals("123-456-789-04", appointment.getPatient().getInsuranceNumber());
        assertEquals("Иван", appointment.getPatient().getUser().getFirstName());
        assertEquals("patient@test.com", appointment.getPatient().getUser().getEmail());
    }

    @Test
    @DisplayName("Получение информации о кабинете")
    void getRoomInfo_returnsCorrectData() {
        assertNotNull(appointment.getRoom());
        assertEquals("101", appointment.getRoom().getCode());
        assertEquals("Кабинет терапевта", appointment.getRoom().getName());
    }

    @Test
    @DisplayName("Все статусы записи")
    void allStatuses_canBeSet() {
        String[] statuses = {"available", "scheduled", "in_progress", "completed", "cancelled", "no_show"};
        
        for (String status : statuses) {
            appointment.setStatus(status);
            assertEquals(status, appointment.getStatus());
        }
    }
}
