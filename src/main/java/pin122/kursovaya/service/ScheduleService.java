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

/**
 * Управление расписаниями врачей и автоматическое порождение слотов {@link Appointment} по длительности интервала.
 */
@Service
public class ScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleService.class);

    private final ScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final RoomRepository roomRepository;

    /**
     * @param scheduleRepository    хранение {@link Schedule}
     * @param appointmentRepository создание слотов приёма
     * @param doctorRepository      проверка врача при создании расписания
     * @param roomRepository        поиск или создание кабинета
     */
    public ScheduleService(ScheduleRepository scheduleRepository,
                          AppointmentRepository appointmentRepository,
                          DoctorRepository doctorRepository,
                          RoomRepository roomRepository) {
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.roomRepository = roomRepository;
    }

    /**
     * Все расписания врача.
     *
     * @param doctorId идентификатор врача
     * @return список {@link pin122.kursovaya.dto.ScheduleDto}
     */
    public List<ScheduleDto> getSchedulesByDoctor(Long doctorId) {
        return scheduleRepository.findByDoctorId(doctorId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Расписания врача; при непустой {@code date} фильтрует по календарной дате.
     *
     * @param doctorId идентификатор врача
     * @param date     день или {@code null} для всех дат
     * @return список {@link pin122.kursovaya.dto.ScheduleDto}
     */
    public List<ScheduleDto> getSchedulesByDoctor(Long doctorId, LocalDate date) {
        if (date != null) {
            return scheduleRepository.findByDoctorIdAndDateAt(doctorId, date).stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
        } else {
            return getSchedulesByDoctor(doctorId);
        }
    }

    /**
     * Все расписания в системе.
     *
     * @return список {@link pin122.kursovaya.dto.ScheduleDto}
     */
    public List<ScheduleDto> getAllSchedules() {
        return scheduleRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Ищет расписание по идентификатору.
     *
     * @param id первичный ключ
     * @return {@link pin122.kursovaya.dto.ScheduleDto}, если найдено
     */
    public Optional<ScheduleDto> getScheduleById(Long id) {
        return scheduleRepository.findById(id)
                .map(this::mapToDto);
    }

    /**
     * Сохраняет расписание и создаёт для него слоты приёма.
     *
     * @param schedule сущность расписания
     * @return DTO сохранённого расписания
     */
    @Transactional
    public ScheduleDto saveSchedule(Schedule schedule) {
        Schedule saved = scheduleRepository.save(schedule);
        
        // Создаем appointments для расписания
        createAppointmentsForSchedule(saved);
        
        return mapToDto(saved);
    }

    /**
     * Создаёт расписание из запроса: загрузка врача, разрешение кабинета по id или коду, генерация слотов.
     *
     * @param request данные расписания и вложенные DTO врача/кабинета
     * @return DTO созданного расписания
     * @throws IllegalArgumentException если не указан врач, врач/кабинет не найдены
     * @throws IllegalStateException      если поле даты не сохранилось в БД
     */
    @Transactional
    public ScheduleDto createSchedule(CreateScheduleRequest request) {
        // Загружаем Doctor из базы данных
        if (request.getDoctor() == null || request.getDoctor().getId() == null) {
            throw new IllegalArgumentException("Doctor ID is required");
        }
        Doctor doctor = doctorRepository.findById(request.getDoctor().getId())
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found with id: " + request.getDoctor().getId()));
        
        // Загружаем или создаём Room
        Room room = null;
        if (request.getRoom() != null) {
            if (request.getRoom().getId() != null) {
                // Если указан ID — ищем существующий кабинет
                room = roomRepository.findById(request.getRoom().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Room not found with id: " + request.getRoom().getId()));
            } else if (request.getRoom().getCode() != null && !request.getRoom().getCode().isBlank()) {
                // Если указан code без ID — ищем по code или создаём новый
                String roomCode = request.getRoom().getCode().trim();
                Optional<Room> existingRoom = roomRepository.findByCode(roomCode);
                
                if (existingRoom.isPresent()) {
                    room = existingRoom.get();
                    logger.info("Найден существующий кабинет по коду '{}': id={}", roomCode, room.getId());
                } else {
                    // Создаём новый кабинет
                    Room newRoom = new Room();
                    newRoom.setCode(roomCode);
                    newRoom.setName(request.getRoom().getName() != null ? 
                            request.getRoom().getName().trim() : "Кабинет " + roomCode);
                    room = roomRepository.save(newRoom);
                    logger.info("Создан новый кабинет: id={}, code='{}', name='{}'", 
                            room.getId(), room.getCode(), room.getName());
                }
            }
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
    
    /**
     * Нарезает интервал {@code startTime}–{@code endTime} на слоты длительностью {@code slotDurationMinutes} и сохраняет как свободные приёмы.
     *
     * @param schedule расписание с заполненными датой, временем и длительностью слота
     */
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
            // Свободный слот для записи: без пациента, статус available (см. AppointmentService / онлайн-запись)
            appointment.setStatus("available");
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

    /**
     * Удаляет расписание по идентификатору.
     *
     * @param id первичный ключ
     */
    public void deleteSchedule(Long id) {
        scheduleRepository.deleteById(id);
    }

    /**
     * Преобразует сущность в DTO без вложенных объектов (только идентификаторы).
     *
     * @param schedule сущность
     * @return {@link pin122.kursovaya.dto.ScheduleDto}
     */
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