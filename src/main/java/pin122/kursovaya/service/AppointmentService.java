package pin122.kursovaya.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final EmailNotificationService emailNotificationService;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    public AppointmentService(AppointmentRepository appointmentRepository, 
                              PatientRepository patientRepository,
                              RedisQueueService redisQueueService,
                              EmailNotificationService emailNotificationService) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.redisQueueService = redisQueueService;
        this.emailNotificationService = emailNotificationService;
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

    /**
     * Получает записи с фильтрацией по врачу, статусу и дате
     * @param doctorId ID врача (опционально)
     * @param status Статус записи (опционально)
     * @param date Дата записи (опционально)
     * @return Отфильтрованный список записей
     */
    public List<AppointmentDto> getAppointmentsFiltered(Long doctorId, String status, LocalDate date) {
        OffsetDateTime startOfDay = null;
        OffsetDateTime startOfNextDay = null;
        
        if (date != null) {
            startOfDay = date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
            startOfNextDay = date.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        }
        
        List<Appointment> appointments;
        
        // Выбираем метод репозитория в зависимости от комбинации фильтров
        if (status != null && doctorId != null && date != null) {
            appointments = appointmentRepository.findByStatusAndDoctorIdAndDate(status, doctorId, startOfDay, startOfNextDay);
        } else if (status != null && doctorId != null) {
            appointments = appointmentRepository.findByStatusAndDoctorId(status, doctorId);
        } else if (status != null && date != null) {
            appointments = appointmentRepository.findByStatusAndDate(status, startOfDay, startOfNextDay);
        } else if (doctorId != null && date != null) {
            appointments = appointmentRepository.findByDoctorIdAndDateRange(doctorId, startOfDay, startOfNextDay);
        } else if (status != null) {
            appointments = appointmentRepository.findByStatus(status);
        } else if (doctorId != null) {
            appointments = appointmentRepository.findByDoctorId(doctorId);
        } else if (date != null) {
            appointments = appointmentRepository.findByDateRange(startOfDay, startOfNextDay);
        } else {
            appointments = appointmentRepository.findAll();
        }
        
        return appointments.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<AppointmentDto> getAppointmentsByDoctor(Long doctorId) {
        return appointmentRepository.findByDoctorIdWithDetails(doctorId).stream()
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
        return appointmentRepository.findByPatientIdWithDetails(patientId).stream()
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
     * Автоматически удаляет пациента из очереди при отмене и пересчитывает очередь
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
        Long doctorId = appointment.getDoctor() != null ? appointment.getDoctor().getId() : null;
        
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
        if (saved.getPatient() != null && doctorId != null) {
            redisQueueService.removeFromQueue(
                saved.getPatient().getId(),
                doctorId
            );
            
            // Пересчитываем очередь и отправляем WebSocket уведомления всем в очереди
            redisQueueService.recalculateQueueForDoctor(doctorId);
        }
        
        // Отправляем email уведомление об отмене
        if (notificationsEnabled && saved.getPatient() != null) {
            emailNotificationService.sendAppointmentCancelledNotification(saved, cancelReason);
        }
        
        return Optional.of(mapToDto(saved));
    }

    /**
     * Обновляет статус приёма и автоматически удаляет пациента из очереди при переходе в terminal статус
     * Также пересчитывает очередь и отправляет WebSocket уведомления всем пациентам в очереди
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
        Long doctorId = appointment.getDoctor() != null ? appointment.getDoctor().getId() : null;
        
        // Проверяем, что статус изменился
        if (newStatus.equals(oldStatus)) {
            return Optional.of(mapToDto(appointment)); // Статус не изменился
        }
        
        appointment.setStatus(newStatus);
        appointment.setUpdatedAt(OffsetDateTime.now());
        
        Appointment saved = appointmentRepository.save(appointment);
        
        // Если статус стал "terminal" → удаляем из очереди и пересчитываем позиции
        if (isTerminalStatus(newStatus) && !isTerminalStatus(oldStatus)) {
            if (saved.getPatient() != null && doctorId != null) {
                redisQueueService.removeFromQueue(
                    saved.getPatient().getId(),
                    doctorId
                );
                
                // Пересчитываем очередь и отправляем WebSocket уведомления всем в очереди
                redisQueueService.recalculateQueueForDoctor(doctorId);
            }
        }
        
        // Отправляем email уведомления об изменении статуса
        if (notificationsEnabled && saved.getPatient() != null) {
            // Уведомление о завершении приёма
            if ("completed".equals(newStatus)) {
                emailNotificationService.sendAppointmentCompletedNotification(saved);
            } 
            // Уведомление об изменении статуса (кроме завершения - для него отдельное письмо)
            else if (!newStatus.equals(oldStatus)) {
                emailNotificationService.sendAppointmentStatusChangedNotification(saved, oldStatus, newStatus);
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
        
        Patient patient = patientOpt.get();
        appointment.setPatient(patient);
        appointment.setStatus("scheduled");
        appointment.setUpdatedAt(java.time.OffsetDateTime.now());
        
        Appointment saved = appointmentRepository.save(appointment);
        
        // Отправляем уведомление о записи
        // Используем patient из контекста транзакции, чтобы гарантировать доступ к User
        if (notificationsEnabled && patient.getUser() != null) {
            saved.setPatient(patient); // Гарантируем, что patient с загруженным user установлен
            emailNotificationService.sendAppointmentBookedNotification(saved);
        }
        
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
        
        AppointmentDto dto = new AppointmentDto(
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
        
        // Заполняем информацию о пациенте
        if (appointment.getPatient() != null) {
            AppointmentDto.PatientInfo patientInfo = new AppointmentDto.PatientInfo();
            patientInfo.setId(appointment.getPatient().getId());
            patientInfo.setBirthDate(appointment.getPatient().getBirthDate() != null ? 
                    appointment.getPatient().getBirthDate().toString() : null);
            patientInfo.setGender(appointment.getPatient().getGender() != null ? 
                    (appointment.getPatient().getGender() == 1 ? "Мужской" : "Женский") : null);
            patientInfo.setInsuranceNumber(appointment.getPatient().getInsuranceNumber());
            
            if (appointment.getPatient().getUser() != null) {
                var user = appointment.getPatient().getUser();
                patientInfo.setFirstName(user.getFirstName());
                patientInfo.setLastName(user.getLastName());
                patientInfo.setMiddleName(user.getMiddleName());
                patientInfo.setPhone(user.getPhone());
                patientInfo.setEmail(user.getEmail());
            }
            dto.setPatient(patientInfo);
        }
        
        // Заполняем информацию о враче
        if (appointment.getDoctor() != null) {
            AppointmentDto.DoctorInfo doctorInfo = new AppointmentDto.DoctorInfo();
            doctorInfo.setId(appointment.getDoctor().getId());
            doctorInfo.setDisplayName(appointment.getDoctor().getDisplayName());
            doctorInfo.setExperienceYears(appointment.getDoctor().getExperienceYears());
            // Конвертируем byte[] в Base64 строку
            if (appointment.getDoctor().getPhoto() != null) {
                doctorInfo.setPhoto(java.util.Base64.getEncoder().encodeToString(appointment.getDoctor().getPhoto()));
            }
            
            if (appointment.getDoctor().getUser() != null) {
                var user = appointment.getDoctor().getUser();
                doctorInfo.setFirstName(user.getFirstName());
                doctorInfo.setLastName(user.getLastName());
                doctorInfo.setMiddleName(user.getMiddleName());
            }
            
            // Получаем первую специализацию
            if (appointment.getDoctor().getSpecializations() != null && 
                !appointment.getDoctor().getSpecializations().isEmpty()) {
                doctorInfo.setSpecialization(
                    appointment.getDoctor().getSpecializations().get(0).getSpecialization().getName()
                );
            }
            dto.setDoctor(doctorInfo);
        }
        
        // Заполняем информацию о кабинете
        if (appointment.getRoom() != null) {
            AppointmentDto.RoomInfo roomInfo = new AppointmentDto.RoomInfo();
            roomInfo.setId(appointment.getRoom().getId());
            roomInfo.setCode(appointment.getRoom().getCode());
            roomInfo.setName(appointment.getRoom().getName());
            dto.setRoom(roomInfo);
        }
        
        return dto;
    }
}