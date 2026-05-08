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
import pin122.kursovaya.repository.ServiceRepository;
import pin122.kursovaya.utils.DoctorPhotoUrls;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Бизнес-логика приёмов: фильтрация, запись пациента, смена статусов, интеграция с Redis-очередью и email.
 */
@Service
public class AppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final ServiceRepository serviceRepository;
    private final RedisQueueService redisQueueService;
    private final EmailNotificationService emailNotificationService;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${app.directus.public-url:http://localhost:8055}")
    private String directusPublicUrl;

    /**
     * @param appointmentRepository   хранение приёмов
     * @param patientRepository       поиск пациента по userId при записи
     * @param serviceRepository       привязка услуги к слоту
     * @param redisQueueService       очередь в Redis при смене терминальных статусов
     * @param emailNotificationService уведомления пациенту
     */
    public AppointmentService(AppointmentRepository appointmentRepository,
                              PatientRepository patientRepository,
                              ServiceRepository serviceRepository,
                              RedisQueueService redisQueueService,
                              EmailNotificationService emailNotificationService) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.serviceRepository = serviceRepository;
        this.redisQueueService = redisQueueService;
        this.emailNotificationService = emailNotificationService;
    }

    /**
     * Возвращает все приёмы в виде DTO (параметры диапазона в сигнатуре не используются репозиторием).
     *
     * @param start    начало периода (зарезервировано)
     * @param end      конец периода (зарезервировано)
     * @param doctorId идентификатор врача (зарезервировано)
     * @return список {@link AppointmentDto}
     */
    public List<AppointmentDto> checkAppointments(Date start, Date end, Long doctorId) {
        return appointmentRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Все приёмы в системе.
     *
     * @return список {@link AppointmentDto}
     */
    public List<AppointmentDto> getAllAppointments() {
        return appointmentRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Возвращает приёмы с комбинированной фильтрацией по врачу, статусу и календарному дню (границы суток в UTC).
     *
     * @param doctorId идентификатор врача или {@code null}
     * @param status   код статуса или {@code null}
     * @param date     дата или {@code null}
     * @return отфильтрованный список {@link AppointmentDto}
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

    /**
     * Приёмы врача с подгруженными деталями для отображения.
     *
     * @param doctorId идентификатор врача
     * @return список {@link AppointmentDto}
     */
    public List<AppointmentDto> getAppointmentsByDoctor(Long doctorId) {
        return appointmentRepository.findByDoctorIdWithDetails(doctorId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Приёмы врача за календарный день (UTC-границы суток).
     *
     * @param doctorId идентификатор врача
     * @param date     дата
     * @return список {@link AppointmentDto}
     */
    public List<AppointmentDto> getAppointmentsByDoctorAndDate(Long doctorId, LocalDate date) {
        OffsetDateTime startOfDay = date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime startOfNextDay = date.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        
        return appointmentRepository.findByDoctorIdAndDate(doctorId, startOfDay, startOfNextDay).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Все приёмы пациента с деталями.
     *
     * @param patientId идентификатор пациента
     * @return список {@link AppointmentDto}
     */
    public List<AppointmentDto> getAppointmentsByPatient(Long patientId) {
        return appointmentRepository.findByPatientIdWithDetails(patientId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Приём по идентификатору с деталями.
     *
     * @param id первичный ключ
     * @return {@link AppointmentDto}, если найден
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<AppointmentDto> getAppointmentById(Long id) {
        return appointmentRepository.findByIdWithDetails(id)
                .map(this::mapToDto);
    }

    /**
     * Сохраняет сущность приёма.
     *
     * @param appointment сущность
     * @return {@link AppointmentDto}
     */
    public AppointmentDto saveAppointment(Appointment appointment) {
        Appointment saved = appointmentRepository.save(appointment);
        return mapToDto(saved);
    }

    /**
     * Удаляет приём по идентификатору.
     *
     * @param id первичный ключ
     */
    public void deleteAppointment(Long id) {
        appointmentRepository.deleteById(id);
    }
    
    /**
     * Переводит приём в статус {@code completed} через {@link #updateAppointmentStatus(Long, String)}.
     *
     * @param appointmentId идентификатор приёма
     * @return обновлённый DTO или пустой {@link Optional}
     */
    @Transactional
    public Optional<AppointmentDto> completeAppointment(Long appointmentId) {
        return updateAppointmentStatus(appointmentId, "completed");
    }

    /**
     * Устанавливает статус {@code cancelled}, убирает пациента из Redis-очереди на день приёма и рассылает уведомления.
     *
     * @param appointmentId идентификатор приёма
     * @param cancelReason    текст причины или {@code null}
     * @return обновлённый {@link AppointmentDto} или пустой {@link Optional}
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
        if (saved.getPatient() != null && doctorId != null && saved.getStartTime() != null) {
            LocalDate queueDay = saved.getStartTime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
            redisQueueService.removeFromQueue(
                saved.getPatient().getId(),
                doctorId,
                queueDay
            );

            redisQueueService.recalculateQueueForDoctor(doctorId, queueDay);
        }
        
        // Отправляем email уведомление об отмене
        if (notificationsEnabled && saved.getPatient() != null) {
            emailNotificationService.sendAppointmentCancelledNotification(saved, cancelReason);
        }
        
        return Optional.of(mapToDto(saved));
    }

    /**
     * Меняет статус приёма; при переходе в терминальный статус синхронизирует {@link RedisQueueService} и шлёт письма.
     *
     * @param appointmentId идентификатор приёма
     * @param newStatus     новый код статуса
     * @return обновлённый {@link AppointmentDto} или пустой {@link Optional}
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
            if (saved.getPatient() != null && doctorId != null && saved.getStartTime() != null) {
                LocalDate queueDay = saved.getStartTime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
                redisQueueService.removeFromQueue(
                    saved.getPatient().getId(),
                    doctorId,
                    queueDay
                );

                redisQueueService.recalculateQueueForDoctor(doctorId, queueDay);
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
     * Признак терминального статуса приёма: {@code completed}, {@code cancelled}, {@code no_show}.
     *
     * @param status код статуса
     * @return {@code true}, если статус терминальный
     */
    private boolean isTerminalStatus(String status) {
        return Set.of("completed", "cancelled", "no_show").contains(status);
    }
    
    /**
     * Свободные слоты врача на дату без фильтра по услуге.
     *
     * @param doctorId идентификатор врача
     * @param date     календарный день
     * @return список доступных слотов в {@link AppointmentDto}
     * @see #getAvailableAppointments(Long, LocalDate, Long)
     */
    public List<AppointmentDto> getAvailableAppointments(Long doctorId, LocalDate date) {
        return getAvailableAppointments(doctorId, date, null);
    }

    /**
     * Свободные слоты за день; при {@code serviceId} остаются слоты без услуги и с совпадающей услугой.
     *
     * @param doctorId  идентификатор врача
     * @param date      день
     * @param serviceId идентификатор услуги или {@code null}
     * @return отфильтрованный список {@link AppointmentDto}
     */
    public List<AppointmentDto> getAvailableAppointments(Long doctorId, LocalDate date, Long serviceId) {
        OffsetDateTime startOfDay = date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime startOfNextDay = date.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);

        List<Appointment> appointments = appointmentRepository.findByDoctorIdAndDateFetchingService(
                doctorId, startOfDay, startOfNextDay);
        return appointments.stream()
                .filter(a -> serviceId == null
                        || a.getService() == null
                        || serviceId.equals(a.getService().getId()))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Календарные даты с хотя бы одним доступным для записи слотом в диапазоне (UTC, согласовано с {@link #getAvailableAppointments}).
     *
     * @param doctorId  врач
     * @param serviceId услуга или {@code null}
     * @param from      начало диапазона или {@code null} (тогда сегодня по UTC)
     * @param to        конец включительно или {@code null} (тогда {@code from + 90} дней)
     * @return отсортированный список дат
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<LocalDate> getAvailableBookingDates(Long doctorId, Long serviceId, LocalDate from, LocalDate to) {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        if (from == null) {
            from = todayUtc;
        }
        if (to == null) {
            to = from.plusDays(90);
        }
        if (to.isBefore(from)) {
            return List.of();
        }
        OffsetDateTime fromTs = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toTs = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime nowTs = OffsetDateTime.now(ZoneOffset.UTC);
        boolean hasServiceFilter = serviceId != null;
        long sidParam = hasServiceFilter ? serviceId : -1L;
        return appointmentRepository.findDistinctBookableDates(
                        doctorId, nowTs, fromTs, toTs, hasServiceFilter, sidParam)
                .stream()
                .map(java.sql.Date::toLocalDate)
                .toList();
    }

    /**
     * Записывает текущего пациента на слот без смены услуги.
     *
     * @param appointmentId идентификатор слота
     * @param userId        идентификатор пользователя (пациента)
     * @return {@link AppointmentDto} или пустой {@link Optional}, если слот занят или пациент не найден
     * @see #bookAppointment(Long, Long, Long)
     */
    @Transactional
    public Optional<AppointmentDto> bookAppointment(Long appointmentId, Long userId) {
        return bookAppointment(appointmentId, userId, null);
    }

    /**
     * Назначает пациента на свободный слот, опционально прикрепляя услугу при совместимости с слотом.
     *
     * @param appointmentId идентификатор слота
     * @param userId        пользователь, от имени которого запись
     * @param serviceId     услуга или {@code null}
     * @return DTO или пустой {@link Optional} при конфликте или отсутствии данных
     */
    @Transactional
    public Optional<AppointmentDto> bookAppointment(Long appointmentId, Long userId, Long serviceId) {
        Optional<Appointment> appointmentOpt = appointmentRepository.findById(appointmentId);
        if (appointmentOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Appointment appointment = appointmentOpt.get();
        
        // Проверяем, что appointment не занят
        if (appointment.getPatient() != null) {
            return Optional.empty(); // Уже занят
        }

        if (serviceId != null) {
            pin122.kursovaya.model.Service existing = appointment.getService();
            if (existing != null && !serviceId.equals(existing.getId())) {
                return Optional.empty();
            }
            if (existing == null) {
                Optional<pin122.kursovaya.model.Service> svcOpt = serviceRepository.findById(serviceId);
                if (svcOpt.isEmpty()) {
                    return Optional.empty();
                }
                appointment.setService(svcOpt.get());
            }
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
    
    /**
     * Маппинг сущности в {@link AppointmentDto}: флаги «занято»/прошедший слот, вложенные patient/doctor/room/diagnosis/service.
     *
     * @param appointment сущность приёма
     * @return DTO для REST
     */
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
                appointment.getComplaints(),
                appointment.getAnamnesis(),
                appointment.getRecommendations(),
                appointment.getDiagnosis() != null ? appointment.getDiagnosis().getId() : null,
                appointment.getService() != null ? appointment.getService().getId() : null
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
            doctorInfo.setPhoto(DoctorPhotoUrls.toPublicImageUrl(
                    appointment.getDoctor().getPhoto(), directusPublicUrl));
            
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
        
        // Заполняем информацию о диагнозе
        if (appointment.getDiagnosis() != null) {
            AppointmentDto.DiagnosisInfo diagnosisInfo = new AppointmentDto.DiagnosisInfo();
            diagnosisInfo.setId(appointment.getDiagnosis().getId());
            diagnosisInfo.setCode(appointment.getDiagnosis().getCode());
            diagnosisInfo.setName(appointment.getDiagnosis().getName());
            diagnosisInfo.setCategory(appointment.getDiagnosis().getCategory());
            dto.setDiagnosis(diagnosisInfo);
        }
        
        // Заполняем информацию об услуге
        if (appointment.getService() != null) {
            AppointmentDto.ServiceInfo serviceInfo = new AppointmentDto.ServiceInfo();
            serviceInfo.setId(appointment.getService().getId());
            serviceInfo.setName(appointment.getService().getName());
            serviceInfo.setCode(appointment.getService().getCode());
            serviceInfo.setPrice(appointment.getService().getPrice());
            serviceInfo.setDurationMinutes(appointment.getService().getDurationMinutes());
            serviceInfo.setDescription(appointment.getService().getDescription());
            dto.setService(serviceInfo);
        }
        
        return dto;
    }
}