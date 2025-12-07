package pin122.kursovaya.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pin122.kursovaya.dto.AppointmentDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.PatientRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final RedisQueueService redisQueueService;

    public AppointmentService(AppointmentRepository appointmentRepository, 
                              PatientRepository patientRepository,
                              RedisQueueService redisQueueService) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.redisQueueService = redisQueueService;
    }

    public List<AppointmentDto> checkAppointments(Date start, Date end, Long doctorId) {
        return appointmentRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<AppointmentDto> getAllAppointments() {
        return appointmentRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<AppointmentDto> getAppointmentsByDoctor(Long doctorId) {
        return appointmentRepository.findByDoctorId(doctorId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<AppointmentDto> getAppointmentsByDoctorAndDate(Long doctorId, LocalDate date) {
        OffsetDateTime startOfDay = date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime startOfNextDay = date.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        
        return appointmentRepository.findByDoctorIdAndDate(doctorId, startOfDay, startOfNextDay).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<AppointmentDto> getAppointmentsByPatient(Long patientId) {
        return appointmentRepository.findByPatientId(patientId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public Optional<AppointmentDto> getAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .map(this::mapToDto);
    }

    public AppointmentDto saveAppointment(Appointment appointment) {
        Appointment saved = appointmentRepository.save(appointment);
        return mapToDto(saved);
    }

    public void deleteAppointment(Long id) {
        appointmentRepository.deleteById(id);
    }
    
    @Transactional
    public Optional<AppointmentDto> completeAppointment(Long appointmentId) {
        return updateAppointmentStatus(appointmentId, "completed");
    }

    /**
     * Отменяет запись на прием (устанавливает статус "cancelled")
     * Автоматически удаляет пациента из очереди при отмене
     * 
     * @param appointmentId ID записи на прием
     * @param cancelReason Причина отмены (опционально)
     * @return Обновлённый AppointmentDto или empty если не найден
     */
    @Transactional
    public Optional<AppointmentDto> cancelAppointment(Long appointmentId, String cancelReason) {
        Optional<Appointment> appointmentOpt = appointmentRepository.findById(appointmentId);
        if (appointmentOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Appointment appointment = appointmentOpt.get();
        String oldStatus = appointment.getStatus();
        
        // Проверяем, что запись еще не отменена
        if ("cancelled".equals(oldStatus)) {
            return Optional.of(mapToDto(appointment)); // Уже отменена
        }
        
        appointment.setStatus("cancelled");
        if (cancelReason != null && !cancelReason.trim().isEmpty()) {
            appointment.setCancelReason(cancelReason.trim());
        }
        appointment.setUpdatedAt(OffsetDateTime.now());
        
        Appointment saved = appointmentRepository.save(appointment);
        
        // Удаляем из очереди, если пациент был в очереди
        if (saved.getPatient() != null && saved.getDoctor() != null) {
            redisQueueService.removeFromQueue(
                saved.getPatient().getId(),
                saved.getDoctor().getId()
            );
        }
        
        return Optional.of(mapToDto(saved));
    }

    /**
     * Обновляет статус приёма и автоматически удаляет пациента из очереди при переходе в terminal статус
     * 
     * @param appointmentId ID приёма
     * @param newStatus Новый статус
     * @return Обновлённый AppointmentDto или empty если не найден
     */
    @Transactional
    public Optional<AppointmentDto> updateAppointmentStatus(Long appointmentId, String newStatus) {
        Optional<Appointment> appointmentOpt = appointmentRepository.findById(appointmentId);
        if (appointmentOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Appointment appointment = appointmentOpt.get();
        String oldStatus = appointment.getStatus();
        
        // Проверяем, что статус изменился
        if (newStatus.equals(oldStatus)) {
            return Optional.of(mapToDto(appointment)); // Статус не изменился
        }
        
        appointment.setStatus(newStatus);
        appointment.setUpdatedAt(OffsetDateTime.now());
        
        Appointment saved = appointmentRepository.save(appointment);
        
        // Если статус стал "terminal" → удаляем из очереди
        if (isTerminalStatus(newStatus) && !isTerminalStatus(oldStatus)) {
            if (saved.getPatient() != null && saved.getDoctor() != null) {
                redisQueueService.removeFromQueue(
                    saved.getPatient().getId(),
                    saved.getDoctor().getId()
                );
            }
        }
        
        return Optional.of(mapToDto(saved));
    }

    /**
     * Проверяет, является ли статус terminal (завершающим)
     * Terminal статусы: completed, cancelled, no_show
     */
    private boolean isTerminalStatus(String status) {
        return Set.of("completed", "cancelled", "no_show").contains(status);
    }
    
    public List<AppointmentDto> getAvailableAppointments(Long doctorId, LocalDate date) {
        // Преобразуем LocalDate в OffsetDateTime для начала и конца дня
        OffsetDateTime startOfDay = date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime startOfNextDay = date.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        
        List<Appointment> appointments = appointmentRepository.findByDoctorIdAndDate(doctorId, startOfDay, startOfNextDay);
        return appointments.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public Optional<AppointmentDto> bookAppointment(Long appointmentId, Long userId) {
        Optional<Appointment> appointmentOpt = appointmentRepository.findById(appointmentId);
        if (appointmentOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Appointment appointment = appointmentOpt.get();
        
        // Проверяем, что appointment не занят
        if (appointment.getPatient() != null) {
            return Optional.empty(); // Уже занят
        }
        
        // Находим пациента по user_id
        Optional<Patient> patientOpt = patientRepository.findByUserId(userId);
        
        if (patientOpt.isEmpty()) {
            return Optional.empty(); // Пациент не найден
        }
        
        appointment.setPatient(patientOpt.get());
        appointment.setStatus("scheduled");
        appointment.setUpdatedAt(java.time.OffsetDateTime.now());
        
        Appointment saved = appointmentRepository.save(appointment);
        return Optional.of(mapToDto(saved));
    }
    
    private AppointmentDto mapToDto(Appointment appointment) {
        java.time.LocalDateTime nowLocal = java.time.LocalDateTime.now();
        boolean hasPatient = appointment.getPatient() != null;
        
        // Сравниваем по локальному времени (без учёта часовых поясов)
        // startTime из БД берём как LocalDateTime, игнорируя Z
        boolean isPastSlot = false;
        if (appointment.getStartTime() != null) {
            java.time.LocalDateTime slotLocal = appointment.getStartTime().toLocalDateTime();
            isPastSlot = slotLocal.isBefore(nowLocal);
        }
        
        // isBooked = true если есть пациент ИЛИ если слот в прошлом (нельзя записаться в прошедший слот)
        boolean isBooked = hasPatient || isPastSlot;
        
        logger.info("=== Appointment ID: {} ===", appointment.getId());
        logger.info("Текущее время (nowLocal): {}", nowLocal);
        logger.info("Время слота (startTime как LocalDateTime): {}", 
                    appointment.getStartTime() != null ? appointment.getStartTime().toLocalDateTime() : null);
        logger.info("Есть пациент (hasPatient): {}", hasPatient);
        logger.info("Слот в прошлом (isPastSlot): {}", isPastSlot);
        logger.info("Итого isBooked: {}", isBooked);
        
        return new AppointmentDto(
                appointment.getId(),
                appointment.getSchedule() != null ? appointment.getSchedule().getId() : null,
                appointment.getDoctor() != null ? appointment.getDoctor().getId() : null,
                appointment.getPatient() != null ? appointment.getPatient().getId() : null,
                appointment.getRoom() != null ? appointment.getRoom().getId() : null,
                appointment.getStartTime(),
                appointment.getEndTime(),
                isBooked,
                appointment.getStatus(),
                appointment.getSource(),
                appointment.getCreatedBy() != null ? appointment.getCreatedBy().getId() : null,
                appointment.getCreatedAt(),
                appointment.getUpdatedAt(),
                appointment.getCancelReason(),
                appointment.getDiagnosis()
        );
    }
}