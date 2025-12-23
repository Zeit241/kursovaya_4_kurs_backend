package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pin122.kursovaya.dto.AppointmentDto;
import pin122.kursovaya.model.*;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.PatientRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для AppointmentService - сервис записей на приём
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentService - тесты сервиса записей на приём")
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private RedisQueueService redisQueueService;

    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private AppointmentService appointmentService;

    private Appointment testAppointment;
    private Doctor testDoctor;
    private Patient testPatient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(appointmentService, "notificationsEnabled", false);

        // Создаём тестового пользователя для врача
        User doctorUser = new User();
        doctorUser.setId(1L);
        doctorUser.setFirstName("Андрей");
        doctorUser.setLastName("Докторов");

        // Создаём тестового врача
        testDoctor = new Doctor();
        testDoctor.setId(1L);
        testDoctor.setUser(doctorUser);
        testDoctor.setDisplayName("Д-р Докторов");
        testDoctor.setExperienceYears(10);

        // Создаём тестового пациента
        User patientUser = new User();
        patientUser.setId(2L);
        patientUser.setFirstName("Пациент");
        patientUser.setLastName("Пациентов");
        patientUser.setEmail("patient@test.com");

        testPatient = new Patient();
        testPatient.setId(1L);
        testPatient.setUser(patientUser);

        // Создаём тестовую запись
        testAppointment = new Appointment();
        testAppointment.setId(1L);
        testAppointment.setDoctor(testDoctor);
        testAppointment.setPatient(null); // Слот свободен
        testAppointment.setStartTime(OffsetDateTime.now().plusDays(1));
        testAppointment.setEndTime(OffsetDateTime.now().plusDays(1).plusMinutes(30));
        testAppointment.setStatus("available");
        testAppointment.setSource("online");
        testAppointment.setCreatedAt(OffsetDateTime.now());
        testAppointment.setUpdatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Получение всех записей")
    void getAllAppointments_returnsList() {
        Appointment appointment2 = new Appointment();
        appointment2.setId(2L);
        appointment2.setDoctor(testDoctor);
        appointment2.setStatus("scheduled");
        appointment2.setSource("online");
        appointment2.setStartTime(OffsetDateTime.now().plusDays(2));
        appointment2.setEndTime(OffsetDateTime.now().plusDays(2).plusMinutes(30));
        appointment2.setCreatedAt(OffsetDateTime.now());
        appointment2.setUpdatedAt(OffsetDateTime.now());

        when(appointmentRepository.findAll()).thenReturn(Arrays.asList(testAppointment, appointment2));

        List<AppointmentDto> result = appointmentService.getAllAppointments();

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(appointmentRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Получение записи по ID - найдена")
    void getAppointmentById_existing_returnsDto() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));

        Optional<AppointmentDto> result = appointmentService.getAppointmentById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("available", result.get().getStatus());
    }

    @Test
    @DisplayName("Получение записи по ID - не найдена")
    void getAppointmentById_notExisting_returnsEmpty() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<AppointmentDto> result = appointmentService.getAppointmentById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Бронирование записи - успешно")
    void bookAppointment_availableSlot_booksSuccessfully() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(testPatient));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Optional<AppointmentDto> result = appointmentService.bookAppointment(1L, 2L);

        assertTrue(result.isPresent());
        assertEquals("scheduled", result.get().getStatus());
        assertNotNull(result.get().getPatientId());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    @DisplayName("Бронирование записи - слот уже занят")
    void bookAppointment_alreadyBooked_returnsEmpty() {
        testAppointment.setPatient(testPatient); // Слот уже занят

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));

        Optional<AppointmentDto> result = appointmentService.bookAppointment(1L, 3L);

        assertFalse(result.isPresent());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    @DisplayName("Бронирование записи - пациент не найден")
    void bookAppointment_patientNotFound_returnsEmpty() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(patientRepository.findByUserId(999L)).thenReturn(Optional.empty());

        Optional<AppointmentDto> result = appointmentService.bookAppointment(1L, 999L);

        assertFalse(result.isPresent());
    }


    @Test
    @DisplayName("Отмена уже отменённой записи - возврат без изменений")
    void cancelAppointment_alreadyCancelled_returnsUnchanged() {
        testAppointment.setStatus("cancelled");

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));

        Optional<AppointmentDto> result = appointmentService.cancelAppointment(1L, "Новая причина");

        assertTrue(result.isPresent());
        assertEquals("cancelled", result.get().getStatus());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    @DisplayName("Обновление статуса записи")
    void updateAppointmentStatus_changesStatus() {
        testAppointment.setPatient(testPatient);
        testAppointment.setStatus("scheduled");

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Optional<AppointmentDto> result = appointmentService.updateAppointmentStatus(1L, "in_progress");

        assertTrue(result.isPresent());
        assertEquals("in_progress", result.get().getStatus());
    }

    @Test
    @DisplayName("Удаление записи")
    void deleteAppointment_callsRepository() {
        doNothing().when(appointmentRepository).deleteById(1L);

        appointmentService.deleteAppointment(1L);

        verify(appointmentRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Получение записей по врачу и дате")
    void getAppointmentsByDoctorAndDate_returnsFilteredList() {
        LocalDate date = LocalDate.now().plusDays(1);

        when(appointmentRepository.findByDoctorIdAndDate(eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(testAppointment));

        List<AppointmentDto> result = appointmentService.getAppointmentsByDoctorAndDate(1L, date);

        assertNotNull(result);
        assertEquals(1, result.size());
    }
}
