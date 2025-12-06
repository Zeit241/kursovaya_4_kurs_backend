package pin122.kursovaya.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.AppointmentDto;
import pin122.kursovaya.dto.AvailableAppointmentDto;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.service.AppointmentService;
import pin122.kursovaya.service.RedisQueueService;

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

    public AppointmentController(AppointmentService appointmentService,
                                RedisQueueService redisQueueService,
                                AppointmentRepository appointmentRepository,
                                UserRepository userRepository,
                                DoctorRepository doctorRepository,
                                PatientRepository patientRepository,
                                SimpMessagingTemplate messagingTemplate) {
        this.appointmentService = appointmentService;
        this.redisQueueService = redisQueueService;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public ResponseEntity<List<AppointmentDto>> getAll() {
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
    public ResponseEntity<List<AvailableAppointmentDto>> getAvailable(
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
}