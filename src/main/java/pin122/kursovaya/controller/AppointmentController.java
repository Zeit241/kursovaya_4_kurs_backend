package pin122.kursovaya.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.AppointmentDto;
import pin122.kursovaya.dto.BookAppointmentRequest;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.DiagnosisRepository;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.service.AppointmentService;
import pin122.kursovaya.service.EmailNotificationService;
import pin122.kursovaya.service.RedisQueueService;
import pin122.kursovaya.service.ReportExportService;
import pin122.kursovaya.utils.SecurityUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST-контроллер записей на приём: фильтрация, бронирование, обновление полей, завершение/отмена, уведомления, PDF.
 * <p>
 * Базовый путь: {@code /api/appointments}. Доступ к части методов ограничен ролями и связью пользователя с врачом/пациентом.
 *
 * @see AppointmentService
 * @see RedisQueueService
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentController.class);

    private final AppointmentService appointmentService;
    private final RedisQueueService redisQueueService;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailNotificationService emailNotificationService;
    private final ReportExportService reportExportService;

    /**
     * @param appointmentService         сервис записей на приём
     * @param redisQueueService          очереди в Redis
     * @param appointmentRepository      прямой доступ к сущностям при обновлении и проверке прав
     * @param userRepository             текущий пользователь и роли
     * @param doctorRepository           связь пользователь–врач
     * @param patientRepository          смена пациента в записи (админ)
     * @param diagnosisRepository        привязка диагноза по id или коду
     * @param messagingTemplate          опциональные push через STOMP
     * @param emailNotificationService   email-уведомления
     * @param reportExportService        генерация PDF талона
     */
    public AppointmentController(AppointmentService appointmentService,
                                RedisQueueService redisQueueService,
                                AppointmentRepository appointmentRepository,
                                UserRepository userRepository,
                                DoctorRepository doctorRepository,
                                PatientRepository patientRepository,
                                DiagnosisRepository diagnosisRepository,
                                SimpMessagingTemplate messagingTemplate,
                                EmailNotificationService emailNotificationService,
                                ReportExportService reportExportService) {
        this.appointmentService = appointmentService;
        this.redisQueueService = redisQueueService;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.messagingTemplate = messagingTemplate;
        this.emailNotificationService = emailNotificationService;
        this.reportExportService = reportExportService;
    }

    /**
     * Возвращает все записи или отфильтрованные по врачу, статусу и/или дате.
     *
     * @param doctorId идентификатор врача (опционально)
     * @param status   строковый статус записи (опционально)
     * @param date     календарная дата в часовом поясе сервиса (опционально)
     * @return HTTP 200 и список {@link AppointmentDto}
     */
    @GetMapping
    public ResponseEntity<List<AppointmentDto>> getAll(
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate date) {
        
        // Если есть хотя бы один фильтр — используем фильтрацию
        if (doctorId != null || status != null || date != null) {
            return ResponseEntity.ok(appointmentService.getAppointmentsFiltered(doctorId, status, date));
        }
        
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }

    /**
     * Служебная выборка всех записей (аналог полного списка без фильтров).
     *
     * @return HTTP 200 и все записи в виде DTO
     */
    @GetMapping("/check")
    public ResponseEntity<List<AppointmentDto>> check() {
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }

    /**
     * Возвращает приёмы текущего авторизованного врача (по JWT); опционально за указанный день.
     *
     * @param date календарная дата или {@code null} для всех записей врача
     * @return HTTP 200 и список DTO; HTTP 401/403/404 при отсутствии авторизации, не-врача или профиля врача
     */
    @GetMapping("/my/doctor")
    public ResponseEntity<?> getMyDoctorAppointments(
            @RequestParam(required = false) LocalDate date) {
        Optional<User> userOpt = SecurityUtils.getCurrentUser(userRepository);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Не авторизован"));
        }
        User user = userOpt.get();
        if (user.getRole() == null || !"doctor".equalsIgnoreCase(user.getRole().getCode())) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступно только врачам"));
        }
        Optional<Doctor> doctorOpt = doctorRepository.findByUserId(user.getId());
        if (doctorOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Профиль врача не найден"));
        }
        Long doctorId = doctorOpt.get().getId();
        if (date != null) {
            return ResponseEntity.ok(appointmentService.getAppointmentsByDoctorAndDate(doctorId, date));
        }
        return ResponseEntity.ok(appointmentService.getAppointmentsByDoctor(doctorId));
    }

    /**
     * Возвращает приёмы указанного врача при наличии прав (админ или сам врач).
     *
     * @param doctorId идентификатор врача
     * @param date     опциональная дата фильтрации
     * @return HTTP 200 и список DTO; HTTP 401/403 при отсутствии прав
     */
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<?> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(required = false) LocalDate date) {
        Optional<User> userOpt = SecurityUtils.getCurrentUser(userRepository);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        User user = userOpt.get();
        if (!canAccessDoctorAppointments(user, doctorId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Нет доступа к приёмам этого врача"));
        }
        if (date != null) {
            return ResponseEntity.ok(appointmentService.getAppointmentsByDoctorAndDate(doctorId, date));
        }
        return ResponseEntity.ok(appointmentService.getAppointmentsByDoctor(doctorId));
    }

    /**
     * Возвращает все записи пациента.
     *
     * @param patientId идентификатор пациента
     * @return HTTP 200 и список {@link AppointmentDto}
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<AppointmentDto>> getByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByPatient(patientId));
    }

    /**
     * Возвращает одну запись с деталями, если текущий пользователь имеет право просмотра.
     *
     * @param id идентификатор записи
     * @return HTTP 200 и DTO; HTTP 401/403/404 при отказе в доступе или отсутствии записи
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Optional<User> userOpt = SecurityUtils.getCurrentUser(userRepository);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Не авторизован"));
        }
        Optional<Appointment> apptOpt = appointmentRepository.findByIdWithDetails(id);
        if (apptOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!canReadAppointment(userOpt.get(), apptOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "Нет доступа к этой записи"));
        }
        return appointmentService.getAppointmentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Возвращает даты, в которых у врача есть свободные слоты для записи (согласовано с {@code GET /available}).
     *
     * @param doctorId  идентификатор врача
     * @param serviceId опциональный идентификатор услуги
     * @param from      начало диапазона дат (опционально)
     * @param to        конец диапазона дат (опционально)
     * @return HTTP 200 и отсортированный список дат
     */
    @GetMapping("/available/dates")
    public ResponseEntity<List<LocalDate>> getAvailableDates(
            @RequestParam Long doctorId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(appointmentService.getAvailableBookingDates(doctorId, serviceId, from, to));
    }

    /**
     * Возвращает свободные слоты врача на конкретную дату с опциональной услугой.
     *
     * @param doctorId  идентификатор врача
     * @param date      дата слотов
     * @param serviceId опциональная услуга
     * @return HTTP 200 и список доступных интервалов в виде DTO
     */
    @GetMapping("/available")
    public ResponseEntity<List<AppointmentDto>> getAvailable(
            @RequestParam Long doctorId,
            @RequestParam LocalDate date,
            @RequestParam(required = false) Long serviceId) {
        return ResponseEntity.ok(appointmentService.getAvailableAppointments(doctorId, date, serviceId));
    }

    /**
     * Бронирует слот для пользователя и опционально привязывает услугу.
     *
     * @param request идентификаторы записи, пользователя и услуги
     * @return HTTP 200 и обновлённая запись или HTTP 400 при неудаче бронирования
     */
    @PostMapping("/book")
    public ResponseEntity<AppointmentDto> book(@Valid @RequestBody BookAppointmentRequest request) {
        logger.info("Booking appointment: appointmentId={}, userId={}, serviceId={}",
                request.getAppointmentId(), request.getUserId(), request.getServiceId());

        return appointmentService.bookAppointment(
                        request.getAppointmentId(),
                        request.getUserId(),
                        request.getServiceId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    /**
     * Создаёт или сохраняет запись на приём из тела сущности (административный сценарий).
     *
     * @param appointment сущность {@link Appointment}
     * @return HTTP 200 и сохранённая запись в виде DTO
     */
    @PostMapping
    public ResponseEntity<AppointmentDto> create(@RequestBody Appointment appointment) {
        return ResponseEntity.ok(appointmentService.saveAppointment(appointment));
    }

    /**
     * Частично обновляет запись JSON-объектом: статус, диагноз, жалобы, пациент (только админ) и др.
     * При переходе в терминальный статус удаляет пациента из Redis-очереди.
     *
     * @param id   идентификатор записи
     * @param body поля для обновления (JSON-объект)
     * @return HTTP 200 с {@code success}, {@code message} и {@code appointment}; HTTP 400/401/403/404 при ошибках
     */
    @PutMapping(value = {"/{id}", "/{id}/"})
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestBody JsonNode body) {
        
        if (body == null || body.isNull() || !body.isObject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ожидался JSON-объект"));
        }
        ArrayList<String> requestKeys = new ArrayList<>();
        body.fieldNames().forEachRemaining(requestKeys::add);
        logger.info("Обновление записи {}: поля в запросе {}", id, requestKeys);
        
        Optional<Appointment> appointmentOpt = appointmentRepository.findById(id);
        if (appointmentOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Запись не найдена"));
        }
        
        Appointment appointment = appointmentOpt.get();

        Optional<User> currentOpt = SecurityUtils.getCurrentUser(userRepository);
        if (currentOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Не авторизован"));
        }
        User currentUser = currentOpt.get();
        if (!isAdmin(currentUser)) {
            if (!isDoctor(currentUser)) {
                return ResponseEntity.status(403).body(Map.of("error", "Нет прав на изменение приёма"));
            }
            Optional<Doctor> doctorOpt = doctorRepository.findByUserId(currentUser.getId());
            if (doctorOpt.isEmpty()
                    || appointment.getDoctor() == null
                    || !doctorOpt.get().getId().equals(appointment.getDoctor().getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Нет прав на изменение этого приёма"));
            }
            if (body.has("patientId")) {
                return ResponseEntity.status(403).body(Map.of("error", "Врач не может менять пациента записи"));
            }
        }

        String oldStatus = appointment.getStatus();
        boolean statusChanged = false;
        String newStatus = null;
        
        // Обновляем поля если они переданы
        if (body.has("status") && !body.get("status").isNull()) {
            newStatus = body.get("status").asText();
            if (!newStatus.equals(oldStatus)) {
                statusChanged = true;
            }
            appointment.setStatus(newStatus);
        }
        
        if (body.has("diagnosis")) {
            applyDiagnosisJson(appointment, body.get("diagnosis"));
        }
        if (body.has("diagnosisId")) {
            JsonNode diagnosisIdNode = body.get("diagnosisId");
            if (diagnosisIdNode == null || diagnosisIdNode.isNull()) {
                appointment.setDiagnosis(null);
            } else if (diagnosisIdNode.isIntegralNumber()) {
                diagnosisRepository.findById(diagnosisIdNode.longValue())
                        .ifPresent(appointment::setDiagnosis);
            } else if (diagnosisIdNode.isTextual()) {
                try {
                    long lid = Long.parseLong(diagnosisIdNode.asText().trim());
                    diagnosisRepository.findById(lid).ifPresent(appointment::setDiagnosis);
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
        }
        
        if (body.has("cancelReason")) {
            appointment.setCancelReason(textFromJsonNullable(body.get("cancelReason")));
        }

        if (body.has("complaints")) {
            appointment.setComplaints(textFromJsonNullable(body.get("complaints")));
        }
        if (body.has("anamnesis")) {
            appointment.setAnamnesis(textFromJsonNullable(body.get("anamnesis")));
        }
        if (body.has("recommendations")) {
            appointment.setRecommendations(textFromJsonNullable(body.get("recommendations")));
        }
        
        if (body.has("patientId")) {
            JsonNode patientIdNode = body.get("patientId");
            if (patientIdNode == null || patientIdNode.isNull()) {
                appointment.setPatient(null);
            } else if (patientIdNode.isIntegralNumber()) {
                patientRepository.findById(patientIdNode.longValue()).ifPresent(appointment::setPatient);
            } else if (patientIdNode.isTextual()) {
                try {
                    patientRepository.findById(Long.parseLong(patientIdNode.asText().trim()))
                            .ifPresent(appointment::setPatient);
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
        }
        
        appointment.setUpdatedAt(java.time.OffsetDateTime.now());
        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        
        // Если статус изменился на terminal (completed, cancelled, no_show) - удаляем из очереди
        if (statusChanged && isTerminalStatus(newStatus) && !isTerminalStatus(oldStatus)) {
            Long doctorId = saved.getDoctor() != null ? saved.getDoctor().getId() : null;
            if (saved.getPatient() != null && doctorId != null && saved.getStartTime() != null) {
                LocalDate queueDay = saved.getStartTime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
                logger.info("Удаление пациента {} из очереди к врачу {} (статус: {})",
                    saved.getPatient().getId(), doctorId, newStatus);
                redisQueueService.removeFromQueue(
                    saved.getPatient().getId(),
                    doctorId,
                    queueDay
                );
                redisQueueService.recalculateQueueForDoctor(doctorId, queueDay);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Запись успешно обновлена");
        response.put("appointment", appointmentService.getAppointmentById(saved.getId()).orElse(null));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Проверяет, является ли статус завершающим для очереди ({@code completed}, {@code cancelled}, {@code no_show}).
     *
     * @param status строковый код статуса
     * @return {@code true}, если статус терминальный
     */
    private boolean isTerminalStatus(String status) {
        return status != null && java.util.Set.of("completed", "cancelled", "no_show").contains(status);
    }

    /**
     * Удаляет запись на приём по идентификатору.
     *
     * @param id идентификатор записи
     * @return HTTP 204 при успехе
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        appointmentService.deleteAppointment(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Завершает приём (админ или ведущий врач); опционально обновляет диагноз из тела запроса.
     * Возвращает обновлённую запись и текущую очередь врача на день приёма.
     *
     * @param id             идентификатор записи
     * @param body           опционально {@code diagnosis}, {@code diagnosisId}
     * @param authentication контекст Spring Security
     * @return HTTP 200 с полями {@code appointment} и {@code queue}; HTTP 401/403/404/400 при ошибках
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeAppointment(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {
        
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Не авторизован"));
        }
        
        // Проверяем права доступа
        String email = authentication.getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Пользователь не найден"));
        }
        
        // Проверяем, является ли пользователь админом
        boolean isAdmin = user.getRole() != null && "admin".equalsIgnoreCase(user.getRole().getCode());
        
        // Получаем appointment
        Optional<Appointment> appointmentOpt = appointmentRepository.findById(id);
        if (appointmentOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Прием не найден"));
        }
        
        Appointment appointment = appointmentOpt.get();
        
        // Если не админ, проверяем, что пользователь является доктором этого приема
        if (!isAdmin) {
            Optional<Doctor> doctorOpt = doctorRepository.findByUserId(user.getId());
            if (doctorOpt.isEmpty() || !doctorOpt.get().getId().equals(appointment.getDoctor().getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Нет прав для завершения этого приема"));
            }
        }

        if (body != null) {
            if (body.containsKey("diagnosis")) {
                applyDiagnosisValue(appointment, body.get("diagnosis"));
            }
            if (body.containsKey("diagnosisId")) {
                Object diagnosisIdObj = body.get("diagnosisId");
                if (diagnosisIdObj == null) {
                    appointment.setDiagnosis(null);
                } else if (diagnosisIdObj instanceof Number) {
                    diagnosisRepository.findById(((Number) diagnosisIdObj).longValue())
                            .ifPresent(appointment::setDiagnosis);
                }
            }
            appointment.setUpdatedAt(java.time.OffsetDateTime.now());
            appointmentRepository.save(appointment);
        }
        
        // Завершаем прием (автоматически удаляет из очереди через AppointmentService)
        Optional<AppointmentDto> completedAppointment = appointmentService.completeAppointment(id);
        if (completedAppointment.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("error", "Не удалось завершить прием"));
        }
        
        Long doctorId = appointment.getDoctor().getId();
        LocalDate queueDay = appointment.getStartTime() != null
                ? appointment.getStartTime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();
        List<QueueEntryDto> updatedQueue = redisQueueService.getQueueByDoctor(doctorId, queueDay);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Прием успешно завершен, пациент автоматически удален из очереди");
        response.put("appointment", completedAppointment.get());
        response.put("queue", updatedQueue);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Отменяет запись (статус {@code cancelled}), опционально с причиной; синхронизирует очередь Redis.
     *
     * @param id      идентификатор записи
     * @param request опционально {@code cancelReason}
     * @return HTTP 200 с {@code appointment} и {@code queue}; HTTP 404 если запись не найдена
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelAppointment(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request) {
        
        String cancelReason = null;
        if (request != null && request.containsKey("cancelReason")) {
            cancelReason = request.get("cancelReason");
        }
        
        Optional<AppointmentDto> cancelledAppointment = appointmentService.cancelAppointment(id, cancelReason);
        if (cancelledAppointment.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Запись на прием не найдена"));
        }
        
        Appointment appointment = appointmentRepository.findById(id).orElse(null);
        if (appointment == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Запись на прием не найдена"));
        }
        
        Long doctorId = appointment.getDoctor().getId();
        LocalDate queueDay = appointment.getStartTime() != null
                ? appointment.getStartTime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();
        List<QueueEntryDto> updatedQueue = redisQueueService.getQueueByDoctor(doctorId, queueDay);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Запись на прием успешно отменена");
        response.put("appointment", cancelledAppointment.get());
        response.put("queue", updatedQueue);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Отправляет email-уведомление пациенту по записи: напоминание, подтверждение записи или завершение.
     *
     * @param id      идентификатор записи
     * @param request опционально {@code type}: {@code reminder}, {@code booked}, {@code completed}
     * @return HTTP 200 при успешной отправке; HTTP 400/404/500 при ошибках данных или почты
     */
    @PostMapping(value = {"/{id}/send_notification", "/{id}/send_notification/"})
    public ResponseEntity<Map<String, Object>> sendNotification(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request) {
        
        logger.info("=== Начало отправки уведомления для записи {} ===", id);
        
        // Используем метод с eager fetch для загрузки всех связанных сущностей
        Optional<Appointment> appointmentOpt;
        try {
            appointmentOpt = appointmentRepository.findByIdWithDetails(id);
            logger.info("Запись найдена: {}", appointmentOpt.isPresent());
        } catch (Exception e) {
            logger.error("Ошибка при поиске записи {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Ошибка при поиске записи",
                "details", e.getMessage()
            ));
        }
        
        if (appointmentOpt.isEmpty()) {
            logger.warn("Запись {} не найдена", id);
            return ResponseEntity.status(404).body(Map.of("error", "Запись на прием не найдена"));
        }
        
        Appointment appointment = appointmentOpt.get();
        logger.info("Appointment ID: {}, Status: {}", appointment.getId(), appointment.getStatus());
        
        if (appointment.getPatient() == null) {
            logger.warn("К записи {} не привязан пациент", id);
            return ResponseEntity.status(400).body(Map.of("error", "К записи не привязан пациент"));
        }
        
        logger.info("Patient ID: {}", appointment.getPatient().getId());
        
        if (appointment.getPatient().getUser() == null) {
            logger.warn("У пациента {} нет связанного пользователя", appointment.getPatient().getId());
            return ResponseEntity.status(400).body(Map.of("error", "У пациента нет связанного пользователя"));
        }
        
        String email = appointment.getPatient().getUser().getEmail();
        logger.info("User ID: {}, Email: {}", appointment.getPatient().getUser().getId(), email);
        
        if (email == null || email.isBlank()) {
            logger.warn("Email не указан для пользователя {}", appointment.getPatient().getUser().getId());
            return ResponseEntity.status(400).body(Map.of("error", "Email пациента не указан"));
        }
        
        // Определяем тип уведомления (по умолчанию - напоминание)
        String notificationType = "reminder";
        if (request != null && request.containsKey("type")) {
            notificationType = request.get("type");
        }
        
        logger.info("Тип уведомления: {}", notificationType);
        
        try {
            switch (notificationType.toLowerCase()) {
                case "booked" -> {
                    logger.info("Отправка уведомления о записи...");
                    emailNotificationService.sendAppointmentBookedNotification(appointment);
                }
                case "completed" -> {
                    logger.info("Отправка уведомления о завершении...");
                    emailNotificationService.sendAppointmentCompletedNotification(appointment);
                }
                case "reminder" -> {
                    logger.info("Отправка напоминания...");
                    emailNotificationService.sendAppointmentReminderNotification(appointment);
                }
                default -> {
                    logger.info("Отправка напоминания (default)...");
                    emailNotificationService.sendAppointmentReminderNotification(appointment);
                }
            }
            
            logger.info("=== Уведомление успешно отправлено для записи {} ===", id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Уведомление успешно отправлено");
            response.put("notificationType", notificationType);
            response.put("appointmentId", id);
            response.put("email", email);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Ошибка при отправке уведомления для записи {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Ошибка при отправке уведомления",
                "details", e.getMessage() != null ? e.getMessage() : "Unknown error",
                "exceptionType", e.getClass().getSimpleName()
            ));
        }
    }
    
    /**
     * Формирует и отдаёт PDF-талон на приём.
     *
     * @param id идентификатор записи
     * @return HTTP 200 с телом {@code application/pdf}; HTTP 404 или 500 при ошибках
     */
    @GetMapping(value = {"/{id}/pdf", "/{id}/pdf/"}, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadAppointmentPdf(@PathVariable Long id) {
        logger.info("Генерация PDF для записи {}", id);
        
        // Загружаем запись со всеми связанными данными
        Optional<Appointment> appointmentOpt = appointmentRepository.findByIdWithDetails(id);
        if (appointmentOpt.isEmpty()) {
            logger.warn("Запись {} не найдена для генерации PDF", id);
            return ResponseEntity.notFound().build();
        }
        
        Appointment appointment = appointmentOpt.get();
        
        try {
            byte[] pdfContent = reportExportService.generateAppointmentPdf(appointment);
            
            String filename = "appointment_" + id + ".pdf";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.add("Content-Disposition", "inline; filename=" + filename);
            
            logger.info("PDF успешно сгенерирован для записи {}", id);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfContent);
                    
        } catch (Exception e) {
            logger.error("Ошибка при генерации PDF для записи {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * @param user пользователь из БД
     * @return {@code true}, если роль {@code admin}
     */
    private static boolean isAdmin(User user) {
        return user.getRole() != null && "admin".equalsIgnoreCase(user.getRole().getCode());
    }

    /**
     * @param user пользователь из БД
     * @return {@code true}, если роль {@code doctor}
     */
    private static boolean isDoctor(User user) {
        return user.getRole() != null && "doctor".equalsIgnoreCase(user.getRole().getCode());
    }

    /**
     * Определяет, может ли пользователь запрашивать приёмы указанного врача.
     *
     * @param user     текущий пользователь
     * @param doctorId идентификатор врача
     * @return {@code true} для админа или для врача, совпадающего с {@code doctorId}
     */
    private boolean canAccessDoctorAppointments(User user, Long doctorId) {
        if (isAdmin(user)) {
            return true;
        }
        if (isDoctor(user)) {
            return doctorRepository.findByUserId(user.getId())
                    .map(d -> d.getId().equals(doctorId))
                    .orElse(false);
        }
        return false;
    }

    /**
     * Проверяет право просмотра одной записи: администратор, врач этой записи или пациент-владелец.
     *
     * @param user        текущий пользователь
     * @param appointment загруженная запись с связями
     * @return {@code true}, если просмотр разрешён
     */
    private boolean canReadAppointment(User user, Appointment appointment) {
        if (isAdmin(user)) {
            return true;
        }
        if (isDoctor(user) && appointment.getDoctor() != null) {
            return doctorRepository.findByUserId(user.getId())
                    .map(d -> d.getId().equals(appointment.getDoctor().getId()))
                    .orElse(false);
        }
        if (appointment.getPatient() != null && appointment.getPatient().getUser() != null
                && appointment.getPatient().getUser().getId().equals(user.getId())) {
            return true;
        }
        return false;
    }

    /**
     * Приводит значение из map-тела запроса к строке или {@code null}.
     *
     * @param value объект из {@link Map}
     * @return непустая строка или {@code null}
     */
    private static String stringFromRequest(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s.isEmpty() ? null : s;
        }
        return String.valueOf(value);
    }

    /**
     * Извлекает текст из {@link JsonNode}: {@code null}, JSON-null и пустая строка дают {@code null}.
     *
     * @param node узел JSON
     * @return текст или {@code null}
     */
    private static String textFromJsonNullable(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String t = node.asText("");
        return t.isEmpty() ? null : t;
    }

    /**
     * Устанавливает диагноз записи из JSON-узла (код строкой или числовой id).
     *
     * @param appointment изменяемая запись
     * @param node        узел {@code diagnosis}
     */
    private void applyDiagnosisJson(Appointment appointment, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            appointment.setDiagnosis(null);
            return;
        }
        if (node.isTextual()) {
            applyDiagnosisValue(appointment, node.asText());
        } else if (node.isIntegralNumber()) {
            diagnosisRepository.findById(node.longValue()).ifPresent(appointment::setDiagnosis);
        }
    }

    /**
     * Устанавливает диагноз из значения тела запроса ({@link String} как код или id, {@link Number} как id).
     *
     * @param appointment   изменяемая запись
     * @param diagnosisObj  значение поля {@code diagnosis} или {@code diagnosisId}
     */
    private void applyDiagnosisValue(Appointment appointment, Object diagnosisObj) {
        if (diagnosisObj == null) {
            appointment.setDiagnosis(null);
            return;
        }
        if (diagnosisObj instanceof String diagnosisCode) {
            diagnosisRepository.findByCode(diagnosisCode)
                    .ifPresentOrElse(
                            appointment::setDiagnosis,
                            () -> {
                                try {
                                    Long parsedId = Long.parseLong(diagnosisCode);
                                    diagnosisRepository.findById(parsedId)
                                            .ifPresent(appointment::setDiagnosis);
                                } catch (NumberFormatException e) {
                                    // ignore
                                }
                            }
                    );
        } else if (diagnosisObj instanceof Number) {
            diagnosisRepository.findById(((Number) diagnosisObj).longValue())
                    .ifPresent(appointment::setDiagnosis);
        }
    }
}