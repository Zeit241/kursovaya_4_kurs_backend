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
import pin122.kursovaya.repository.ServiceRepository;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
    private ServiceRepository serviceRepository;

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
        when(appointmentRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(testAppointment));

        Optional<AppointmentDto> result = appointmentService.getAppointmentById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("available", result.get().getStatus());
    }

    @Test
    @DisplayName("Получение записи по ID - не найдена")
    void getAppointmentById_notExisting_returnsEmpty() {
        when(appointmentRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

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

    @Test
    @DisplayName("Завершение приёма — статус completed")
    void completeAppointment_existing_setsCompletedStatus() {
        testAppointment.setPatient(testPatient);
        testAppointment.setStatus("in_progress");

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Optional<AppointmentDto> result = appointmentService.completeAppointment(1L);

        assertTrue(result.isPresent());
        assertEquals("completed", result.get().getStatus());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    @DisplayName("Завершение приёма — запись не найдена")
    void completeAppointment_notFound_returnsEmpty() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<AppointmentDto> result = appointmentService.completeAppointment(999L);

        assertFalse(result.isPresent());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    @DisplayName("Свободные слоты врача на дату без фильтра по услуге")
    void getAvailableAppointments_withoutServiceId_returnsAllSlots() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(1);

        when(appointmentRepository.findByDoctorIdAndDateFetchingService(
                eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(testAppointment));

        List<AppointmentDto> result = appointmentService.getAvailableAppointments(1L, date);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    @DisplayName("Свободные слоты — фильтр по услуге, совпадающая услуга")
    void getAvailableAppointments_withMatchingServiceId_returnsSlot() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        pin122.kursovaya.model.Service service = new pin122.kursovaya.model.Service();
        service.setId(5L);
        service.setName("Консультация");
        testAppointment.setService(service);

        when(appointmentRepository.findByDoctorIdAndDateFetchingService(
                eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(testAppointment));

        List<AppointmentDto> result = appointmentService.getAvailableAppointments(1L, date, 5L);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getServiceId());
    }

    @Test
    @DisplayName("Свободные слоты — фильтр по услуге, слот без услуги включается")
    void getAvailableAppointments_withServiceId_includesSlotWithoutService() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        testAppointment.setService(null);

        when(appointmentRepository.findByDoctorIdAndDateFetchingService(
                eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(testAppointment));

        List<AppointmentDto> result = appointmentService.getAvailableAppointments(1L, date, 5L);

        assertEquals(1, result.size());
        assertNull(result.get(0).getServiceId());
    }

    @Test
    @DisplayName("Свободные слоты — фильтр по услуге исключает другую услугу")
    void getAvailableAppointments_withServiceId_excludesDifferentService() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        pin122.kursovaya.model.Service otherService = new pin122.kursovaya.model.Service();
        otherService.setId(99L);
        testAppointment.setService(otherService);

        when(appointmentRepository.findByDoctorIdAndDateFetchingService(
                eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(testAppointment));

        List<AppointmentDto> result = appointmentService.getAvailableAppointments(1L, date, 5L);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Доступные даты записи — диапазон по умолчанию")
    void getAvailableBookingDates_nullFromTo_usesDefaults() {
        LocalDate expected = LocalDate.now(ZoneOffset.UTC).plusDays(3);

        when(appointmentRepository.findDistinctBookableDates(
                eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class), any(OffsetDateTime.class),
                eq(false), eq(-1L)))
                .thenReturn(List.of(Date.valueOf(expected)));

        List<LocalDate> result = appointmentService.getAvailableBookingDates(1L, null, null, null);

        assertEquals(1, result.size());
        assertEquals(expected, result.get(0));
    }

    @Test
    @DisplayName("Доступные даты записи — явный диапазон")
    void getAvailableBookingDates_explicitRange_returnsDates() {
        LocalDate from = LocalDate.of(2025, 6, 1);
        LocalDate to = LocalDate.of(2025, 6, 10);
        LocalDate bookable = LocalDate.of(2025, 6, 5);

        when(appointmentRepository.findDistinctBookableDates(
                eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class), any(OffsetDateTime.class),
                eq(false), eq(-1L)))
                .thenReturn(List.of(Date.valueOf(bookable)));

        List<LocalDate> result = appointmentService.getAvailableBookingDates(1L, null, from, to);

        assertEquals(List.of(bookable), result);
    }

    @Test
    @DisplayName("Доступные даты записи — to раньше from возвращает пустой список")
    void getAvailableBookingDates_toBeforeFrom_returnsEmpty() {
        LocalDate from = LocalDate.of(2025, 6, 10);
        LocalDate to = LocalDate.of(2025, 6, 1);

        List<LocalDate> result = appointmentService.getAvailableBookingDates(1L, null, from, to);

        assertTrue(result.isEmpty());
        verify(appointmentRepository, never()).findDistinctBookableDates(
                anyLong(), any(), any(), any(), anyBoolean(), anyLong());
    }

    @Test
    @DisplayName("Доступные даты записи — фильтр по услуге")
    void getAvailableBookingDates_withServiceFilter_passesServiceId() {
        LocalDate from = LocalDate.of(2025, 6, 1);
        LocalDate to = LocalDate.of(2025, 6, 30);
        LocalDate bookable = LocalDate.of(2025, 6, 15);

        when(appointmentRepository.findDistinctBookableDates(
                eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class), any(OffsetDateTime.class),
                eq(true), eq(7L)))
                .thenReturn(List.of(Date.valueOf(bookable)));

        List<LocalDate> result = appointmentService.getAvailableBookingDates(1L, 7L, from, to);

        assertEquals(List.of(bookable), result);
    }

    @Test
    @DisplayName("Получение записей по врачу")
    void getAppointmentsByDoctor_returnsMappedList() {
        when(appointmentRepository.findByDoctorIdWithDetails(1L)).thenReturn(List.of(testAppointment));

        List<AppointmentDto> result = appointmentService.getAppointmentsByDoctor(1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getDoctorId());
        verify(appointmentRepository).findByDoctorIdWithDetails(1L);
    }

    @Test
    @DisplayName("Получение записей по пациенту")
    void getAppointmentsByPatient_returnsMappedList() {
        testAppointment.setPatient(testPatient);
        when(appointmentRepository.findByPatientIdWithDetails(1L)).thenReturn(List.of(testAppointment));

        List<AppointmentDto> result = appointmentService.getAppointmentsByPatient(1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getPatientId());
        verify(appointmentRepository).findByPatientIdWithDetails(1L);
    }

    @Test
    @DisplayName("Обновление статуса на completed — удаление из Redis-очереди")
    void updateAppointmentStatus_toCompleted_removesFromRedisQueue() {
        testAppointment.setPatient(testPatient);
        testAppointment.setStatus("scheduled");
        LocalDate queueDay = testAppointment.getStartTime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Optional<AppointmentDto> result = appointmentService.updateAppointmentStatus(1L, "completed");

        assertTrue(result.isPresent());
        assertEquals("completed", result.get().getStatus());
        verify(redisQueueService).removeFromQueue(1L, 1L, queueDay);
        verify(redisQueueService).recalculateQueueForDoctor(1L, queueDay);
    }

    @Test
    @DisplayName("Обновление статуса на no_show — удаление из Redis-очереди")
    void updateAppointmentStatus_toNoShow_removesFromRedisQueue() {
        testAppointment.setPatient(testPatient);
        testAppointment.setStatus("scheduled");
        LocalDate queueDay = testAppointment.getStartTime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Optional<AppointmentDto> result = appointmentService.updateAppointmentStatus(1L, "no_show");

        assertTrue(result.isPresent());
        assertEquals("no_show", result.get().getStatus());
        verify(redisQueueService).removeFromQueue(1L, 1L, queueDay);
        verify(redisQueueService).recalculateQueueForDoctor(1L, queueDay);
    }

    @Test
    @DisplayName("Обновление статуса — тот же статус без сохранения и Redis")
    void updateAppointmentStatus_sameStatus_returnsUnchangedWithoutSideEffects() {
        testAppointment.setPatient(testPatient);
        testAppointment.setStatus("scheduled");

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));

        Optional<AppointmentDto> result = appointmentService.updateAppointmentStatus(1L, "scheduled");

        assertTrue(result.isPresent());
        assertEquals("scheduled", result.get().getStatus());
        verify(appointmentRepository, never()).save(any(Appointment.class));
        verify(redisQueueService, never()).removeFromQueue(anyLong(), anyLong(), any(LocalDate.class));
    }

    @Test
    @DisplayName("Обновление статуса — запись не найдена")
    void updateAppointmentStatus_notFound_returnsEmpty() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<AppointmentDto> result = appointmentService.updateAppointmentStatus(999L, "completed");

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Обновление статуса — переход между терминальными статусами без Redis")
    void updateAppointmentStatus_terminalToTerminal_skipsRedis() {
        testAppointment.setPatient(testPatient);
        testAppointment.setStatus("completed");

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Optional<AppointmentDto> result = appointmentService.updateAppointmentStatus(1L, "cancelled");

        assertTrue(result.isPresent());
        assertEquals("cancelled", result.get().getStatus());
        verify(redisQueueService, never()).removeFromQueue(anyLong(), anyLong(), any(LocalDate.class));
        verify(redisQueueService, never()).recalculateQueueForDoctor(anyLong(), any(LocalDate.class));
    }

    @Test
    @DisplayName("Отмена записи — успех с синхронизацией Redis")
    void cancelAppointment_activeAppointment_removesFromRedisQueue() {
        testAppointment.setPatient(testPatient);
        testAppointment.setStatus("scheduled");
        LocalDate queueDay = testAppointment.getStartTime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Optional<AppointmentDto> result = appointmentService.cancelAppointment(1L, "Пациент не может прийти");

        assertTrue(result.isPresent());
        assertEquals("cancelled", result.get().getStatus());
        assertEquals("Пациент не может прийти", result.get().getCancelReason());
        verify(redisQueueService).removeFromQueue(1L, 1L, queueDay);
        verify(redisQueueService).recalculateQueueForDoctor(1L, queueDay);
    }

    @Test
    @DisplayName("Отмена записи — запись не найдена")
    void cancelAppointment_notFound_returnsEmpty() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<AppointmentDto> result = appointmentService.cancelAppointment(999L, "Причина");

        assertFalse(result.isPresent());
        verify(redisQueueService, never()).removeFromQueue(anyLong(), anyLong(), any(LocalDate.class));
    }

    @Test
    @DisplayName("Отмена записи — пустая причина не сохраняется")
    void cancelAppointment_blankReason_doesNotSetCancelReason() {
        testAppointment.setPatient(testPatient);
        testAppointment.setStatus("scheduled");

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Optional<AppointmentDto> result = appointmentService.cancelAppointment(1L, "   ");

        assertTrue(result.isPresent());
        assertEquals("cancelled", result.get().getStatus());
        assertNull(result.get().getCancelReason());
    }

    @Test
    @DisplayName("Сохранение записи — возвращает DTO")
    void saveAppointment_persistsAndReturnsDto() {
        when(appointmentRepository.save(testAppointment)).thenReturn(testAppointment);

        AppointmentDto result = appointmentService.saveAppointment(testAppointment);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("available", result.getStatus());
        verify(appointmentRepository).save(testAppointment);
    }

    @Test
    @DisplayName("Фильтрация записей — только по врачу")
    void getAppointmentsFiltered_byDoctorIdOnly() {
        when(appointmentRepository.findByDoctorId(1L)).thenReturn(List.of(testAppointment));

        List<AppointmentDto> result = appointmentService.getAppointmentsFiltered(1L, null, null);

        assertEquals(1, result.size());
        verify(appointmentRepository).findByDoctorId(1L);
        verify(appointmentRepository, never()).findAll();
    }

    @Test
    @DisplayName("Бронирование — запись не найдена")
    void bookAppointment_appointmentNotFound_returnsEmpty() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<AppointmentDto> result = appointmentService.bookAppointment(999L, 2L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Бронирование — несовместимая услуга со слотом")
    void bookAppointment_incompatibleService_returnsEmpty() {
        pin122.kursovaya.model.Service slotService = new pin122.kursovaya.model.Service();
        slotService.setId(10L);
        testAppointment.setService(slotService);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));

        Optional<AppointmentDto> result = appointmentService.bookAppointment(1L, 2L, 5L);

        assertFalse(result.isPresent());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    @DisplayName("Фильтрация записей — врач, статус и дата")
    void getAppointmentsFiltered_byDoctorStatusAndDate() {
        LocalDate date = LocalDate.of(2025, 6, 15);

        when(appointmentRepository.findByStatusAndDoctorIdAndDate(
                eq("scheduled"), eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(testAppointment));

        List<AppointmentDto> result = appointmentService.getAppointmentsFiltered(1L, "scheduled", date);

        assertEquals(1, result.size());
        verify(appointmentRepository).findByStatusAndDoctorIdAndDate(
                eq("scheduled"), eq(1L), any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Фильтрация записей — без фильтров возвращает все записи")
    void getAppointmentsFiltered_noFilters_returnsAll() {
        when(appointmentRepository.findAll()).thenReturn(Collections.singletonList(testAppointment));

        List<AppointmentDto> result = appointmentService.getAppointmentsFiltered(null, null, null);

        assertEquals(1, result.size());
        verify(appointmentRepository).findAll();
    }
}
