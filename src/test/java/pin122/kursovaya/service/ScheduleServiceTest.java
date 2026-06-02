package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pin122.kursovaya.dto.CreateScheduleRequest;
import pin122.kursovaya.dto.ScheduleDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Room;
import pin122.kursovaya.model.Schedule;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.RoomRepository;
import pin122.kursovaya.repository.ScheduleRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService - тесты расписания")
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    private Doctor doctor;
    private Schedule schedule;
    private LocalDate scheduleDate;

    @BeforeEach
    void setUp() {
        scheduleDate = LocalDate.of(2026, 6, 15);
        doctor = new Doctor();
        doctor.setId(1L);

        schedule = new Schedule();
        schedule.setId(10L);
        schedule.setDoctor(doctor);
        schedule.setDateAt(scheduleDate);
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(12, 0));
        schedule.setSlotDurationMinutes(30);
        schedule.setCreatedAt(OffsetDateTime.now());
        schedule.setUpdatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("getSchedulesByDoctor возвращает список DTO")
    void getSchedulesByDoctor_returnsList() {
        when(scheduleRepository.findByDoctorId(1L)).thenReturn(List.of(schedule));

        List<ScheduleDto> result = scheduleService.getSchedulesByDoctor(1L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
        assertEquals(1L, result.get(0).getDoctorId());
        verify(scheduleRepository).findByDoctorId(1L);
    }

    @Test
    @DisplayName("getSchedulesByDoctor с датой фильтрует по дню")
    void getSchedulesByDoctor_withDate_filtersByDate() {
        when(scheduleRepository.findByDoctorIdAndDateAt(1L, scheduleDate)).thenReturn(List.of(schedule));

        List<ScheduleDto> result = scheduleService.getSchedulesByDoctor(1L, scheduleDate);

        assertEquals(1, result.size());
        assertEquals(scheduleDate, result.get(0).getDateAt());
        verify(scheduleRepository).findByDoctorIdAndDateAt(1L, scheduleDate);
        verify(scheduleRepository, never()).findByDoctorId(anyLong());
    }

    @Test
    @DisplayName("getSchedulesByDoctor с null датой возвращает все расписания врача")
    void getSchedulesByDoctor_nullDate_returnsAll() {
        when(scheduleRepository.findByDoctorId(1L)).thenReturn(List.of(schedule));

        List<ScheduleDto> result = scheduleService.getSchedulesByDoctor(1L, null);

        assertEquals(1, result.size());
        verify(scheduleRepository).findByDoctorId(1L);
    }

    @Test
    @DisplayName("getAllSchedules возвращает все расписания")
    void getAllSchedules_returnsAll() {
        when(scheduleRepository.findAll()).thenReturn(List.of(schedule));

        List<ScheduleDto> result = scheduleService.getAllSchedules();

        assertEquals(1, result.size());
        verify(scheduleRepository).findAll();
    }

    @Test
    @DisplayName("getScheduleById возвращает DTO для существующего расписания")
    void getScheduleById_found() {
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));

        Optional<ScheduleDto> result = scheduleService.getScheduleById(10L);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getId());
    }

    @Test
    @DisplayName("getScheduleById возвращает empty для отсутствующего расписания")
    void getScheduleById_notFound() {
        when(scheduleRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<ScheduleDto> result = scheduleService.getScheduleById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("saveSchedule сохраняет расписание и создаёт слоты приёма")
    void saveSchedule_savesAndCreatesAppointments() {
        when(scheduleRepository.save(schedule)).thenReturn(schedule);

        ScheduleDto result = scheduleService.saveSchedule(schedule);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        verify(scheduleRepository).save(schedule);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Appointment>> captor = ArgumentCaptor.forClass(List.class);
        verify(appointmentRepository).saveAll(captor.capture());
        assertEquals(6, captor.getValue().size());
        assertEquals("available", captor.getValue().get(0).getStatus());
    }

    @Test
    @DisplayName("createSchedule без врача выбрасывает IllegalArgumentException")
    void createSchedule_missingDoctor_throws() {
        CreateScheduleRequest request = new CreateScheduleRequest();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> scheduleService.createSchedule(request));

        assertEquals("Doctor ID is required", ex.getMessage());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSchedule с несуществующим врачом выбрасывает исключение")
    void createSchedule_doctorNotFound_throws() {
        CreateScheduleRequest request = new CreateScheduleRequest();
        CreateScheduleRequest.DoctorIdDto doctorDto = new CreateScheduleRequest.DoctorIdDto();
        doctorDto.setId(999L);
        request.setDoctor(doctorDto);

        when(doctorRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> scheduleService.createSchedule(request));

        assertTrue(ex.getMessage().contains("Doctor not found"));
    }

    @Test
    @DisplayName("createSchedule использует существующий кабинет по id")
    void createSchedule_withExistingRoom() {
        Room room = new Room();
        room.setId(5L);
        room.setCode("101");

        CreateScheduleRequest request = buildCreateRequest(1L);
        CreateScheduleRequest.RoomIdDto roomDto = new CreateScheduleRequest.RoomIdDto();
        roomDto.setId(5L);
        request.setRoom(roomDto);
        request.setDateAt(scheduleDate);
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(10, 0));
        request.setSlotDurationMinutes(30);

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(roomRepository.findById(5L)).thenReturn(Optional.of(room));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.setId(20L);
            s.setCreatedAt(OffsetDateTime.now());
            s.setUpdatedAt(OffsetDateTime.now());
            return s;
        });

        ScheduleDto result = scheduleService.createSchedule(request);

        assertNotNull(result);
        assertEquals(5L, result.getRoomId());
        verify(roomRepository).findById(5L);
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSchedule создаёт новый кабинет по коду")
    void createSchedule_createsNewRoomByCode() {
        CreateScheduleRequest request = buildCreateRequest(1L);
        CreateScheduleRequest.RoomIdDto roomDto = new CreateScheduleRequest.RoomIdDto();
        roomDto.setCode("NEW-201");
        roomDto.setName("Кабинет 201");
        request.setRoom(roomDto);
        request.setDateAt(scheduleDate);
        request.setStartTime(LocalTime.of(14, 0));
        request.setEndTime(LocalTime.of(15, 0));
        request.setSlotDurationMinutes(30);

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(roomRepository.findByCode("NEW-201")).thenReturn(Optional.empty());
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> {
            Room r = inv.getArgument(0);
            r.setId(30L);
            return r;
        });
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            s.setId(21L);
            s.setCreatedAt(OffsetDateTime.now());
            s.setUpdatedAt(OffsetDateTime.now());
            return s;
        });

        ScheduleDto result = scheduleService.createSchedule(request);

        assertNotNull(result);
        verify(roomRepository).save(argThat(r -> "NEW-201".equals(r.getCode())));
    }

    @Test
    @DisplayName("deleteSchedule вызывает удаление в репозитории")
    void deleteSchedule_callsRepository() {
        scheduleService.deleteSchedule(10L);

        verify(scheduleRepository).deleteById(10L);
    }

    private CreateScheduleRequest buildCreateRequest(Long doctorId) {
        CreateScheduleRequest request = new CreateScheduleRequest();
        CreateScheduleRequest.DoctorIdDto doctorDto = new CreateScheduleRequest.DoctorIdDto();
        doctorDto.setId(doctorId);
        request.setDoctor(doctorDto);
        return request;
    }
}
