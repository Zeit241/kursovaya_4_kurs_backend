package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pin122.kursovaya.dto.DailyReportDto;
import pin122.kursovaya.dto.ReportAppointmentDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.Room;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.DoctorRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для ReportService - сервис формирования отчётов
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService - тесты сервиса отчётов")
class ReportServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @InjectMocks
    private ReportService reportService;

    private LocalDate reportDate;
    private Doctor testDoctor;
    private Patient testPatient;

    @BeforeEach
    void setUp() {
        reportDate = LocalDate.of(2025, 6, 15);

        User doctorUser = new User();
        doctorUser.setId(1L);
        doctorUser.setFirstName("Андрей");
        doctorUser.setLastName("Докторов");
        doctorUser.setPhone("+79001111111");
        doctorUser.setEmail("doctor@test.com");

        testDoctor = new Doctor();
        testDoctor.setId(1L);
        testDoctor.setUser(doctorUser);

        User patientUser = new User();
        patientUser.setId(2L);
        patientUser.setFirstName("Иван");
        patientUser.setLastName("Петров");
        patientUser.setMiddleName("Сергеевич");
        patientUser.setPhone("+79002222222");
        patientUser.setEmail("patient@test.com");

        testPatient = new Patient();
        testPatient.setId(5L);
        testPatient.setUser(patientUser);
        testPatient.setBirthDate(LocalDate.of(1990, 1, 1));
        testPatient.setGender((short) 1);
        testPatient.setInsuranceNumber("1234567890123456");
    }

    private Appointment createAppointment(String status) {
        Room room = new Room();
        room.setId(3L);
        room.setCode("101");

        Appointment appointment = new Appointment();
        appointment.setId(100L);
        appointment.setDoctor(testDoctor);
        appointment.setPatient(testPatient);
        appointment.setRoom(room);
        appointment.setStartTime(reportDate.atStartOfDay().atOffset(ZoneOffset.UTC).plusHours(10));
        appointment.setEndTime(reportDate.atStartOfDay().atOffset(ZoneOffset.UTC).plusHours(10).plusMinutes(30));
        appointment.setStatus(status);
        appointment.setCreatedAt(OffsetDateTime.now());
        return appointment;
    }

    @Test
    @DisplayName("Отчёт за день - подсчёт статусов scheduled и confirmed")
    void getAllAppointmentsByDate_countsScheduledAndConfirmed() {
        Appointment scheduled = createAppointment("scheduled");
        Appointment confirmed = createAppointment("confirmed");
        confirmed.setId(101L);

        when(appointmentRepository.findAllByDate(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(scheduled, confirmed));

        DailyReportDto result = reportService.getAllAppointmentsByDate(reportDate);

        assertEquals(reportDate, result.getDate());
        assertEquals(2, result.getTotalAppointments());
        assertEquals(2, result.getScheduledCount());
        assertEquals(0, result.getCompletedCount());
        assertEquals(0, result.getCancelledCount());
        assertEquals(0, result.getNoShowCount());
        assertEquals(2, result.getAppointments().size());
    }

    @Test
    @DisplayName("Отчёт за день - пустой день")
    void getAllAppointmentsByDate_emptyDay_returnsZeroCounts() {
        when(appointmentRepository.findAllByDate(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());

        DailyReportDto result = reportService.getAllAppointmentsByDate(reportDate);

        assertEquals(0, result.getTotalAppointments());
        assertTrue(result.getAppointments().isEmpty());
        assertNull(result.getDoctorId());
        assertNull(result.getDoctorDisplayName());
    }

    @Test
    @DisplayName("Отчёт по врачу за день - с именем врача")
    void getAppointmentsByDoctorAndDate_includesDoctorName() {
        Appointment completed = createAppointment("completed");

        when(appointmentRepository.findByDoctorIdAndDateForReport(
                eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(completed));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        DailyReportDto result = reportService.getAppointmentsByDoctorAndDate(1L, reportDate);

        assertEquals(1L, result.getDoctorId());
        assertNotNull(result.getDoctorDisplayName());
        assertEquals(1, result.getCompletedCount());
        assertEquals(0, result.getScheduledCount());
    }

    @Test
    @DisplayName("Отчёт по врачу за день - врач не найден")
    void getAppointmentsByDoctorAndDate_doctorNotFound_nullDisplayName() {
        when(appointmentRepository.findByDoctorIdAndDateForReport(
                eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(doctorRepository.findById(1L)).thenReturn(Optional.empty());

        DailyReportDto result = reportService.getAppointmentsByDoctorAndDate(1L, reportDate);

        assertEquals(1L, result.getDoctorId());
        assertNull(result.getDoctorDisplayName());
        assertEquals(0, result.getTotalAppointments());
    }

    @Test
    @DisplayName("Отчёт за период - несколько дней")
    void getAppointmentsByDateRange_returnsAggregatedReport() {
        Appointment cancelled = createAppointment("cancelled");
        Appointment noShow = createAppointment("no_show");
        noShow.setId(102L);

        when(appointmentRepository.findAllByDateRange(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(cancelled, noShow));

        LocalDate start = LocalDate.of(2025, 6, 1);
        LocalDate end = LocalDate.of(2025, 6, 30);

        DailyReportDto result = reportService.getAppointmentsByDateRange(start, end);

        assertEquals(start, result.getDate());
        assertEquals(2, result.getTotalAppointments());
        assertEquals(1, result.getCancelledCount());
        assertEquals(1, result.getNoShowCount());
    }

    @Test
    @DisplayName("Отчёт по врачу за период - с именем врача")
    void getAppointmentsByDoctorAndDateRange_includesDoctorInfo() {
        Appointment scheduled = createAppointment("scheduled");

        when(appointmentRepository.findByDoctorIdAndDateRangeForReport(
                eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(scheduled));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        LocalDate start = LocalDate.of(2025, 6, 1);
        LocalDate end = LocalDate.of(2025, 6, 15);

        DailyReportDto result = reportService.getAppointmentsByDoctorAndDateRange(1L, start, end);

        assertEquals(1L, result.getDoctorId());
        assertNotNull(result.getDoctorDisplayName());
        assertEquals(1, result.getScheduledCount());
        assertEquals(1, result.getTotalAppointments());
    }

    @Test
    @DisplayName("Отчёт - подсчёт cancelled и no_show")
    void getAllAppointmentsByDate_countsCancelledAndNoShow() {
        Appointment cancelled = createAppointment("cancelled");
        Appointment noShow = createAppointment("no_show");
        noShow.setId(103L);

        when(appointmentRepository.findAllByDate(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(cancelled, noShow));

        DailyReportDto result = reportService.getAllAppointmentsByDate(reportDate);

        assertEquals(2, result.getTotalAppointments());
        assertEquals(1, result.getCancelledCount());
        assertEquals(1, result.getNoShowCount());
    }

    @Test
    @DisplayName("Отчёт - маппинг данных пациента в DTO")
    void getAllAppointmentsByDate_mapsPatientInfo() {
        Appointment appointment = createAppointment("scheduled");

        when(appointmentRepository.findAllByDate(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(appointment));

        DailyReportDto result = reportService.getAllAppointmentsByDate(reportDate);

        assertEquals(1, result.getAppointments().size());
        ReportAppointmentDto dto = result.getAppointments().get(0);
        assertEquals(100L, dto.getAppointmentId());
        assertEquals(5L, dto.getPatientId());
        assertEquals("Петров Иван Сергеевич", dto.getPatientFullName());
        assertEquals("patient@test.com", dto.getPatientEmail());
        assertEquals("Мужской", dto.getPatientGender());
        assertEquals(1L, dto.getDoctorId());
        assertEquals(3L, dto.getRoomId());
        assertEquals("101", dto.getRoomNumber());
    }
}
