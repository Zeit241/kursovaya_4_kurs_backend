package pin122.kursovaya.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.AppointmentDto;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.service.AppointmentService;
import pin122.kursovaya.service.EmailNotificationService;
import pin122.kursovaya.service.RedisQueueService;
import pin122.kursovaya.service.ReportExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailNotificationService emailNotificationService;
    private final ReportExportService reportExportService;

    public AppointmentController(AppointmentService appointmentService,
                                RedisQueueService redisQueueService,
                                AppointmentRepository appointmentRepository,
                                UserRepository userRepository,
                                DoctorRepository doctorRepository,
                                PatientRepository patientRepository,
                                SimpMessagingTemplate messagingTemplate,
                                EmailNotificationService emailNotificationService,
                                ReportExportService reportExportService) {
        this.appointmentService = appointmentService;
        this.redisQueueService = redisQueueService;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.messagingTemplate = messagingTemplate;
        this.emailNotificationService = emailNotificationService;
        this.reportExportService = reportExportService;
    }

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

    @GetMapping("/check")
    public ResponseEntity<List<AppointmentDto>> check() {
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<AppointmentDto>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(required = false) LocalDate date) {
        if (date != null) {
            return ResponseEntity.ok(appointmentService.getAppointmentsByDoctorAndDate(doctorId, date));
        }
        return ResponseEntity.ok(appointmentService.getAppointmentsByDoctor(doctorId));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<AppointmentDto>> getByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByPatient(patientId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentDto> getById(@PathVariable Long id) {
        return appointmentService.getAppointmentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/available")
    public ResponseEntity<List<AppointmentDto>> getAvailable(
            @RequestParam Long doctorId,
            @RequestParam LocalDate date) {
        return ResponseEntity.ok(appointmentService.getAvailableAppointments(doctorId, date));
    }

    @PostMapping("/book")
    public ResponseEntity<AppointmentDto> book(@RequestBody Map<String, Long> request) {
        Long appointmentId = request.get("appointmentId");
        Long userId = request.get("userId");

        logger.info("Booking appointment: appointmentId={}, userId={}", appointmentId, userId);
        
        if (appointmentId == null || userId == null) {
            logger.error("Invalid request: appointmentId={}, userId={}", appointmentId, userId);
            return ResponseEntity.badRequest().build();
        }
        
        return appointmentService.bookAppointment(appointmentId, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping
    public ResponseEntity<AppointmentDto> create(@RequestBody Appointment appointment) {
        return ResponseEntity.ok(appointmentService.saveAppointment(appointment));
    }

    @PutMapping(value = {"/{id}", "/{id}/"})
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        
        logger.info("Обновление записи {}: {}", id, request);
        
        Optional<Appointment> appointmentOpt = appointmentRepository.findById(id);
        if (appointmentOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Запись не найдена"));
        }
        
        Appointment appointment = appointmentOpt.get();
        
        // Обновляем поля если они переданы
        if (request.containsKey("status")) {
            String newStatus = (String) request.get("status");
            appointment.setStatus(newStatus);
        }
        
        if (request.containsKey("diagnosis")) {
            String diagnosis = (String) request.get("diagnosis");
            appointment.setDiagnosis(diagnosis);
        }
        
        if (request.containsKey("cancelReason")) {
            String cancelReason = (String) request.get("cancelReason");
            appointment.setCancelReason(cancelReason);
        }
        
        if (request.containsKey("patientId")) {
            Object patientIdObj = request.get("patientId");
            if (patientIdObj == null) {
                appointment.setPatient(null);
            } else {
                Long patientId = patientIdObj instanceof Integer ? 
                    ((Integer) patientIdObj).longValue() : (Long) patientIdObj;
                patientRepository.findById(patientId).ifPresent(appointment::setPatient);
            }
        }
        
        appointment.setUpdatedAt(java.time.OffsetDateTime.now());
        Appointment saved = appointmentRepository.save(appointment);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Запись успешно обновлена");
        response.put("appointment", appointmentService.getAppointmentById(saved.getId()).orElse(null));
        
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        appointmentService.deleteAppointment(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Завершить прием (доступно для админа и доктора)
     * После завершения автоматически обновляются очереди всех пациентов к этому врачу
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeAppointment(
            @PathVariable Long id,
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
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "admin".equalsIgnoreCase(role.getCode()));
        
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
        
        // Завершаем прием (автоматически удаляет из очереди через AppointmentService)
        Optional<AppointmentDto> completedAppointment = appointmentService.completeAppointment(id);
        if (completedAppointment.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("error", "Не удалось завершить прием"));
        }
        
        // Получаем очередь к врачу (WebSocket уведомления уже отправлены через RedisQueueService)
        Long doctorId = appointment.getDoctor().getId();
        List<QueueEntryDto> updatedQueue = redisQueueService.getQueueByDoctor(doctorId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Прием успешно завершен, пациент автоматически удален из очереди");
        response.put("appointment", completedAppointment.get());
        response.put("queue", updatedQueue);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Отменить запись на прием (устанавливает статус "cancelled")
     * Автоматически удаляет пациента из очереди при отмене
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
        
        // Получаем обновленную очередь к врачу
        Long doctorId = appointment.getDoctor().getId();
        List<QueueEntryDto> updatedQueue = redisQueueService.getQueueByDoctor(doctorId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Запись на прием успешно отменена");
        response.put("appointment", cancelledAppointment.get());
        response.put("queue", updatedQueue);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Отправить уведомление (напоминание) о записи на прием
     * @param id ID записи на прием
     * @param request Опциональные параметры: type (тип уведомления: reminder, booked, completed)
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
     * Скачать PDF талон на приём
     * @param id ID записи на прием
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
}