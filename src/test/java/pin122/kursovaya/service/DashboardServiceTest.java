package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pin122.kursovaya.dto.DashboardAppointmentDto;
import pin122.kursovaya.dto.DashboardDto;
import pin122.kursovaya.dto.DoctorDto;
import pin122.kursovaya.dto.SpecializationStatsDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.DoctorSpecialization;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.Room;
import pin122.kursovaya.model.Specialization;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.SpecializationRepository;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для DashboardService - сервис данных главной страницы
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService - тесты сервиса дашборда")
class DashboardServiceTest {

    @Mock
    private SpecializationRepository specializationRepository;

    @Mock
    private DoctorService doctorService;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private Patient testPatient;
    private Appointment scheduledAppointment;
    private Specialization testSpecialization;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dashboardService, "directusPublicUrl", "http://localhost:8055");

        User patientUser = new User();
        patientUser.setId(2L);
        patientUser.setFirstName("Иван");
        patientUser.setLastName("Петров");

        testPatient = new Patient();
        testPatient.setId(10L);
        testPatient.setUser(patientUser);

        User doctorUser = new User();
        doctorUser.setId(1L);
        doctorUser.setFirstName("Андрей");
        doctorUser.setLastName("Докторов");
        doctorUser.setMiddleName("Иванович");

        testSpecialization = new Specialization();
        testSpecialization.setId(1L);
        testSpecialization.setCode("cardiology");
        testSpecialization.setName("Кардиология");
        testSpecialization.setDescription("Сердце");

        DoctorSpecialization doctorSpec = new DoctorSpecialization();
        doctorSpec.setSpecialization(testSpecialization);

        Doctor doctor = new Doctor();
        doctor.setId(1L);
        doctor.setUser(doctorUser);
        doctor.setPhoto("photo-uuid");
        doctor.setSpecializations(List.of(doctorSpec));

        Room room = new Room();
        room.setId(3L);
        room.setCode("101");
        room.setName("Кабинет 101");

        scheduledAppointment = new Appointment();
        scheduledAppointment.setId(100L);
        scheduledAppointment.setDoctor(doctor);
        scheduledAppointment.setPatient(testPatient);
        scheduledAppointment.setRoom(room);
        scheduledAppointment.setStartTime(OffsetDateTime.now().plusDays(1));
        scheduledAppointment.setEndTime(OffsetDateTime.now().plusDays(1).plusMinutes(30));
        scheduledAppointment.setStatus("scheduled");
        scheduledAppointment.setSource("online");
        scheduledAppointment.setCreatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Получение данных дашборда - полный ответ")
    void getDashboardData_returnsFullDashboard() {
        Specialization spec2 = new Specialization();
        spec2.setId(2L);
        spec2.setCode("neurology");
        spec2.setName("Неврология");

        Object[] row1 = {testSpecialization, 5L};
        Object[] row2 = {spec2, 3L};

        DoctorDto doctorDto = new DoctorDto();
        doctorDto.setId(1L);

        when(specializationRepository.findTopSpecializationsByDoctorCount())
                .thenReturn(List.of(row1, row2));
        when(doctorService.getAllDoctors(10, 0, "rating", "desc"))
                .thenReturn(List.of(doctorDto));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(testPatient));
        when(appointmentRepository.findScheduledAppointmentsByPatient(10L))
                .thenReturn(List.of(scheduledAppointment));

        DashboardDto result = dashboardService.getDashboardData(2L);

        assertNotNull(result);
        assertNotNull(result.getTopSpecializations());
        assertNotNull(result.getTopDoctors());
        assertNotNull(result.getUpcomingAppointments());
        assertEquals(2, result.getTopSpecializations().size());
        assertEquals(1, result.getTopDoctors().size());
        assertEquals(1, result.getUpcomingAppointments().size());
    }

    @Test
    @DisplayName("Дашборд - userId null, пустые приёмы")
    void getDashboardData_nullUserId_returnsEmptyAppointments() {
        when(specializationRepository.findTopSpecializationsByDoctorCount())
                .thenReturn(Collections.emptyList());
        when(doctorService.getAllDoctors(10, 0, "rating", "desc"))
                .thenReturn(Collections.emptyList());

        DashboardDto result = dashboardService.getDashboardData(null);

        assertNotNull(result.getUpcomingAppointments());
        assertTrue(result.getUpcomingAppointments().isEmpty());
        verify(patientRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("Дашборд - пользователь не пациент, пустые приёмы")
    void getDashboardData_userNotPatient_returnsEmptyAppointments() {
        when(specializationRepository.findTopSpecializationsByDoctorCount())
                .thenReturn(Collections.emptyList());
        when(doctorService.getAllDoctors(10, 0, "rating", "desc"))
                .thenReturn(Collections.emptyList());
        when(patientRepository.findByUserId(99L)).thenReturn(Optional.empty());

        DashboardDto result = dashboardService.getDashboardData(99L);

        assertTrue(result.getUpcomingAppointments().isEmpty());
        verify(appointmentRepository, never()).findScheduledAppointmentsByPatient(any());
    }

    @Test
    @DisplayName("Дашборд - предстоящие приёмы пациента")
    void getDashboardData_patientHasAppointments_returnsScheduled() {
        when(specializationRepository.findTopSpecializationsByDoctorCount())
                .thenReturn(Collections.emptyList());
        when(doctorService.getAllDoctors(10, 0, "rating", "desc"))
                .thenReturn(Collections.emptyList());
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(testPatient));
        when(appointmentRepository.findScheduledAppointmentsByPatient(10L))
                .thenReturn(List.of(scheduledAppointment));

        DashboardDto result = dashboardService.getDashboardData(2L);

        assertEquals(1, result.getUpcomingAppointments().size());
        DashboardAppointmentDto appt = result.getUpcomingAppointments().get(0);
        assertEquals(100L, appt.getId());
        assertEquals("scheduled", appt.getStatus());
    }

    @Test
    @DisplayName("Дашборд - топ специализаций ограничен 5 записями")
    void getDashboardData_topSpecializations_limitedToFive() {
        Object[] row1 = {testSpecialization, 10L};
        Object[] row2 = {new Specialization(), 9L};
        Object[] row3 = {new Specialization(), 8L};
        Object[] row4 = {new Specialization(), 7L};
        Object[] row5 = {new Specialization(), 6L};
        Object[] row6 = {new Specialization(), 5L};

        when(specializationRepository.findTopSpecializationsByDoctorCount())
                .thenReturn(List.of(row1, row2, row3, row4, row5, row6));
        when(doctorService.getAllDoctors(10, 0, "rating", "desc"))
                .thenReturn(Collections.emptyList());
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(testPatient));
        when(appointmentRepository.findScheduledAppointmentsByPatient(10L))
                .thenReturn(Collections.emptyList());

        DashboardDto result = dashboardService.getDashboardData(2L);

        assertEquals(5, result.getTopSpecializations().size());
        SpecializationStatsDto first = result.getTopSpecializations().get(0);
        assertEquals("cardiology", first.getCode());
        assertEquals(10L, first.getDoctorCount());
    }

    @Test
    @DisplayName("Дашборд - топ врачей запрашивается с параметрами рейтинга")
    void getDashboardData_topDoctors_callsDoctorServiceWithRatingSort() {
        when(specializationRepository.findTopSpecializationsByDoctorCount())
                .thenReturn(Collections.emptyList());
        when(doctorService.getAllDoctors(10, 0, "rating", "desc"))
                .thenReturn(Collections.emptyList());
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(testPatient));
        when(appointmentRepository.findScheduledAppointmentsByPatient(10L))
                .thenReturn(Collections.emptyList());

        dashboardService.getDashboardData(2L);

        verify(doctorService, times(1)).getAllDoctors(10, 0, "rating", "desc");
    }

    @Test
    @DisplayName("Дашборд - маппинг ФИО врача в приёме")
    void getDashboardData_appointmentMapping_includesDoctorName() {
        when(specializationRepository.findTopSpecializationsByDoctorCount())
                .thenReturn(Collections.emptyList());
        when(doctorService.getAllDoctors(10, 0, "rating", "desc"))
                .thenReturn(Collections.emptyList());
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(testPatient));
        when(appointmentRepository.findScheduledAppointmentsByPatient(10L))
                .thenReturn(List.of(scheduledAppointment));

        DashboardDto result = dashboardService.getDashboardData(2L);

        DashboardAppointmentDto appt = result.getUpcomingAppointments().get(0);
        assertEquals("Андрей", appt.getDoctorFirstName());
        assertEquals("Докторов", appt.getDoctorLastName());
        assertEquals("Иванович", appt.getDoctorMiddleName());
        assertEquals(1L, appt.getDoctorId());
    }

    @Test
    @DisplayName("Дашборд - маппинг кабинета в приёме")
    void getDashboardData_appointmentMapping_includesRoomName() {
        when(specializationRepository.findTopSpecializationsByDoctorCount())
                .thenReturn(Collections.emptyList());
        when(doctorService.getAllDoctors(10, 0, "rating", "desc"))
                .thenReturn(Collections.emptyList());
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(testPatient));
        when(appointmentRepository.findScheduledAppointmentsByPatient(10L))
                .thenReturn(List.of(scheduledAppointment));

        DashboardDto result = dashboardService.getDashboardData(2L);

        DashboardAppointmentDto appt = result.getUpcomingAppointments().get(0);
        assertEquals(3L, appt.getRoomId());
        assertEquals("Кабинет 101", appt.getRoomName());
    }
}
