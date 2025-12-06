package pin122.kursovaya.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleService.class);

    private final ScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final RoomRepository roomRepository;

    public ScheduleService(ScheduleRepository scheduleRepository, 
                          AppointmentRepository appointmentRepository,
                          DoctorRepository doctorRepository,
                          RoomRepository roomRepository) {
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.roomRepository = roomRepository;
    }

    public List<ScheduleDto> getSchedulesByDoctor(Long doctorId) {
        return scheduleRepository.findByDoctorId(doctorId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<ScheduleDto> getSchedulesByDoctor(Long doctorId, LocalDate date) {
        if (date != null) {
            return scheduleRepository.findByDoctorIdAndDateAt(doctorId, date).stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
        } else {
            return getSchedulesByDoctor(doctorId);
        }
    }

    public List<ScheduleDto> getAllSchedules() {
        return scheduleRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public Optional<ScheduleDto> getScheduleById(Long id) {
        return scheduleRepository.findById(id)
                .map(this::mapToDto);
    }

    @Transactional
    public ScheduleDto saveSchedule(Schedule schedule) {
        Schedule saved = scheduleRepository.save(schedule);
        
        // Создаем appointments для расписания
        createAppointmentsForSchedule(saved);
        
        return mapToDto(saved);
    }

    @Transactional
    public ScheduleDto createSchedule(CreateScheduleRequest request) {
        // Загружаем Doctor из базы данных
        if (request.getDoctor() == null || request.getDoctor().getId() == null) {
            throw new IllegalArgumentException("Doctor ID is required");
        }
        Doctor doctor = doctorRepository.findById(request.getDoctor().getId())
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found with id: " + request.getDoctor().getId()));
        
        // Загружаем Room из базы данных (если указан)
        Room room = null;
        if (request.getRoom() != null && request.getRoom().getId() != null) {
            room = roomRepository.findById(request.getRoom().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Room not found with id: " + request.getRoom().getId()));
        }
        
        // Создаем объект Schedule
        Schedule schedule = new Schedule();
        schedule.setDoctor(doctor);
        schedule.setRoom(room);
        schedule.setDateAt(request.getDateAt());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setSlotDurationMinutes(request.getSlotDurationMinutes());
        
        // Сохраняем расписание
        Schedule saved = scheduleRepository.save(schedule);
        
        // Проверяем, что dateAt сохранился
        if (saved.getDateAt() == null && request.getDateAt() != null) {
            logger.error("dateAt не сохранился! Ожидалось: {}, получено: null", request.getDateAt());
            throw new IllegalStateException("Failed to save dateAt. Expected: " + request.getDateAt() + ", but got null");
        }
        
        // Создаем appointments для расписания
        createAppointmentsForSchedule(saved);
        
        return mapToDto(saved);
    }
    
    private void createAppointmentsForSchedule(Schedule schedule) {
        if (schedule.getDateAt() == null || schedule.getStartTime() == null || 
            schedule.getEndTime() == null || schedule.getSlotDurationMinutes() == null) {
            return;
        }
        
        LocalDate date = schedule.getDateAt();
        LocalTime startTime = schedule.getStartTime();
        LocalTime endTime = schedule.getEndTime();
        int durationMinutes = schedule.getSlotDurationMinutes();
        
        List<Appointment> appointments = new ArrayList<>();
        LocalTime currentTime = startTime;
        
        // Создаем слоты от начала до конца с интервалом durationMinutes
        while (!currentTime.plusMinutes(durationMinutes).isAfter(endTime)) {
            LocalTime slotEndTime = currentTime.plusMinutes(durationMinutes);
            
            // Преобразуем LocalDate и LocalTime в OffsetDateTime
            LocalDateTime startDateTime = LocalDateTime.of(date, currentTime);
            LocalDateTime endDateTime = LocalDateTime.of(date, slotEndTime);
            OffsetDateTime startOffset = startDateTime.atOffset(ZoneOffset.UTC);
            OffsetDateTime endOffset = endDateTime.atOffset(ZoneOffset.UTC);
            
            Appointment appointment = new Appointment();
            appointment.setSchedule(schedule);
            appointment.setDoctor(schedule.getDoctor());
            appointment.setPatient(null);
            appointment.setRoom(schedule.getRoom());
            appointment.setStartTime(startOffset);
            appointment.setEndTime(endOffset);
            appointment.setStatus("scheduled");
            appointment.setSource("admin");
            appointment.setCreatedBy(null);
            
            appointments.add(appointment);
            currentTime = slotEndTime;
        }
        
        // Сохраняем все appointments
        if (!appointments.isEmpty()) {
            appointmentRepository.saveAll(appointments);
        }
    }

    public void deleteSchedule(Long id) {
        scheduleRepository.deleteById(id);
    }

    private ScheduleDto mapToDto(Schedule schedule) {
        return new ScheduleDto(
                schedule.getId(),
                schedule.getDoctor() != null ? schedule.getDoctor().getId() : null,
                schedule.getRoom() != null ? schedule.getRoom().getId() : null,
                schedule.getDateAt(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getSlotDurationMinutes(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }
}