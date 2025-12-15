package pin122.kursovaya.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import pin122.kursovaya.dto.AppointmentDto;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.dto.QueuePositionDto;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.service.AppointmentService;
import pin122.kursovaya.service.RedisQueueService;

import java.util.List;
import java.util.Optional;

/**
 * WebSocket контроллер для работы с электронной очередью
 * Авторизация через JWT токен при подключении
 * Использует только Redis для хранения очередей
 */
@Controller
public class QueueWebSocketController {

    private final RedisQueueService redisQueueService;
    private final AppointmentService appointmentService;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public QueueWebSocketController(RedisQueueService redisQueueService,
                                    AppointmentService appointmentService,
                                    UserRepository userRepository,
                                    PatientRepository patientRepository,
                                    SimpMessagingTemplate messagingTemplate) {
        this.redisQueueService = redisQueueService;
        this.appointmentService = appointmentService;
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Обработка подключения - автоматически строит очередь на текущий день
     * Отправляется при подключении клиента
     */
    @MessageMapping("/queue/init")
    public void initQueue(Authentication authentication) {
        System.out.println("DEBUG WebSocket: initQueue вызван, authentication: " + (authentication != null ? authentication.getName() : "null"));
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                System.out.println("DEBUG WebSocket: Пользователь не авторизован");
                messagingTemplate.convertAndSendToUser(
                    authentication != null ? authentication.getName() : "anonymous",
                    "/queue/user",
                    new QueueInitResponse(false, "Пользователь не авторизован", null)
                );
                return;
            }

            String email = authentication.getName();
            System.out.println("DEBUG WebSocket: Email пользователя: " + email);
            User user = userRepository.findByEmail(email);
            if (user == null) {
                System.out.println("DEBUG WebSocket: Пользователь не найден в БД");
                messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/user",
                    new QueueInitResponse(false, "Пользователь не найден", null)
                );
                return;
            }

            Optional<Patient> patient = patientRepository.findByUserId(user.getId());
            if (patient.isEmpty()) {
                System.out.println("DEBUG WebSocket: Пользователь не является пациентом, user_id: " + user.getId());
                messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/user",
                    new QueueInitResponse(false, "Пользователь не является пациентом", null)
                );
                return;
            }

            System.out.println("DEBUG WebSocket: Найден пациент с ID: " + patient.get().getId());
            // Строим очередь на текущий день (только Redis)
            List<QueueEntryDto> queueEntries = redisQueueService.buildQueueForToday(patient.get().getId());
            System.out.println("DEBUG WebSocket: Построено записей в очереди: " + queueEntries.size());

            messagingTemplate.convertAndSendToUser(
                email,
                "/queue/user",
                new QueueInitResponse(true, 
                    queueEntries.isEmpty() 
                        ? "Нет активных записей на сегодня" 
                        : "Очередь на сегодня успешно построена",
                    queueEntries)
            );
        } catch (Exception e) {
            String email = authentication != null ? authentication.getName() : "anonymous";
            messagingTemplate.convertAndSendToUser(
                email,
                "/queue/user",
                new QueueInitResponse(false, "Ошибка при инициализации очереди: " + e.getMessage(), null)
            );
        }
    }

    /**
     * Обновление статуса приема через WebSocket
     * При изменении статуса очередь автоматически пересчитывается
     */
    @MessageMapping("/queue/status-update")
    public void handleStatusUpdate(@Payload StatusUpdateRequest request, Authentication authentication) {
        System.out.println("DEBUG WebSocket: handleStatusUpdate вызван для appointmentId: " + request.getAppointmentId());
        try {
            if (authentication == null) {
                messagingTemplate.convertAndSendToUser(
                    "anonymous",
                    "/queue/user",
                    new StatusUpdateResponse(false, "Пользователь не авторизован", null)
                );
                return;
            }

            String email = authentication.getName();
            
            // Проверяем валидность запроса
            if (request.getAppointmentId() == null || request.getNewStatus() == null) {
                messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/user",
                    new StatusUpdateResponse(false, "Не указан appointmentId или newStatus", null)
                );
                return;
            }

            // Обновляем статус через AppointmentService
            // Это автоматически пересчитает очередь и отправит уведомления
            Optional<AppointmentDto> updated = appointmentService.updateAppointmentStatus(
                request.getAppointmentId(), 
                request.getNewStatus()
            );

            if (updated.isEmpty()) {
                messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/user",
                    new StatusUpdateResponse(false, "Запись не найдена", null)
                );
                return;
            }

            messagingTemplate.convertAndSendToUser(
                email,
                "/queue/user",
                new StatusUpdateResponse(true, "Статус успешно обновлен", updated.get())
            );

            System.out.println("DEBUG WebSocket: Статус обновлен для appointmentId: " + 
                    request.getAppointmentId() + " -> " + request.getNewStatus());

        } catch (Exception e) {
            String email = authentication != null ? authentication.getName() : "anonymous";
            messagingTemplate.convertAndSendToUser(
                email,
                "/queue/user",
                new StatusUpdateResponse(false, "Ошибка при обновлении статуса: " + e.getMessage(), null)
            );
        }
    }

    /**
     * Получить позицию в очереди к врачу
     */
    @MessageMapping("/queue/position")
    public void getPosition(@Payload QueuePositionRequest request, Authentication authentication) {
        try {
            if (authentication == null) {
                messagingTemplate.convertAndSendToUser(
                    "anonymous",
                    "/queue/user",
                    new QueuePositionResponse(false, "Пользователь не авторизован", null)
                );
                return;
            }

            String email = authentication.getName();
            User user = userRepository.findByEmail(email);
            if (user == null) {
                messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/user",
                    new QueuePositionResponse(false, "Пользователь не найден", null)
                );
                return;
            }

            Optional<Patient> patient = patientRepository.findByUserId(user.getId());
            if (patient.isEmpty()) {
                messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/user",
                    new QueuePositionResponse(false, "Пользователь не является пациентом", null)
                );
                return;
            }

            Integer position = redisQueueService.getPatientPosition(
                    patient.get().getId(),
                    request.getDoctorId()
            );

            if (position == null) {
                messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/user",
                    new QueuePositionResponse(false, "Пользователь не находится в очереди к этому врачу", null)
                );
                return;
            }

            boolean isNext = redisQueueService.isPatientNextInQueue(patient.get().getId(), request.getDoctorId());
            
            // Получаем очередь для создания DTO
            List<QueueEntryDto> queue = redisQueueService.getQueueByDoctor(request.getDoctorId());
            QueueEntryDto entry = queue.stream()
                    .filter(e -> patient.get().getId().equals(e.getPatientId()))
                    .findFirst()
                    .orElse(null);
            
            if (entry == null) {
                messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/user",
                    new QueuePositionResponse(false, "Запись в очереди не найдена", null)
                );
                return;
            }

            QueuePositionDto positionDto = new QueuePositionDto(
                    entry.getId(),
                    entry.getDoctorId(),
                    entry.getPatientId(),
                    entry.getPosition(),
                    isNext,
                    isNext ? "Вы следующий в очереди" : "В очереди перед вами есть другие пациенты"
            );

            messagingTemplate.convertAndSendToUser(
                email,
                "/queue/user",
                new QueuePositionResponse(true, "Позиция в очереди получена", positionDto)
            );
        } catch (Exception e) {
            String email = authentication != null ? authentication.getName() : "anonymous";
            messagingTemplate.convertAndSendToUser(
                email,
                "/queue/user",
                new QueuePositionResponse(false, "Ошибка: " + e.getMessage(), null)
            );
        }
    }

    /**
     * Получить все очереди пользователя
     */
    @MessageMapping("/queue/my-queues")
    public void getMyQueues(Authentication authentication) {
        System.out.println("DEBUG WebSocket: getMyQueues вызван (возвращает существующие записи из БД)");
        try {
            if (authentication == null) {
                messagingTemplate.convertAndSendToUser(
                    "anonymous",
                    "/queue/user",
                    new QueueListResponse(false, "Пользователь не авторизован", null)
                );
                return;
            }

            String email = authentication.getName();
            System.out.println("DEBUG WebSocket: getMyQueues для пользователя: " + email);
            User user = userRepository.findByEmail(email);
            if (user == null) {
                messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/user",
                    new QueueListResponse(false, "Пользователь не найден", null)
                );
                return;
            }

            Optional<Patient> patient = patientRepository.findByUserId(user.getId());
            if (patient.isEmpty()) {
                messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/user",
                    new QueueListResponse(false, "Пользователь не является пациентом", null)
                );
                return;
            }

            // Получаем все очереди для пациента
            List<QueueEntryDto> queues = redisQueueService.getQueuesByPatient(patient.get().getId());
            messagingTemplate.convertAndSendToUser(
                email,
                "/queue/user",
                new QueueListResponse(true, "Очереди получены", queues)
            );
        } catch (Exception e) {
            String email = authentication != null ? authentication.getName() : "anonymous";
            messagingTemplate.convertAndSendToUser(
                email,
                "/queue/user",
                new QueueListResponse(false, "Ошибка: " + e.getMessage(), null)
            );
        }
    }

    // DTO классы для WebSocket сообщений
    public static class QueueInitResponse {
        private boolean success;
        private String message;
        private List<QueueEntryDto> data;

        public QueueInitResponse(boolean success, String message, List<QueueEntryDto> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<QueueEntryDto> getData() { return data; }
        public void setData(List<QueueEntryDto> data) { this.data = data; }
    }

    public static class QueuePositionResponse {
        private boolean success;
        private String message;
        private QueuePositionDto data;

        public QueuePositionResponse(boolean success, String message, QueuePositionDto data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public QueuePositionDto getData() { return data; }
        public void setData(QueuePositionDto data) { this.data = data; }
    }

    public static class QueueListResponse {
        private boolean success;
        private String message;
        private List<QueueEntryDto> data;

        public QueueListResponse(boolean success, String message, List<QueueEntryDto> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<QueueEntryDto> getData() { return data; }
        public void setData(List<QueueEntryDto> data) { this.data = data; }
    }

    public static class QueuePositionRequest {
        private Long doctorId;

        public Long getDoctorId() { return doctorId; }
        public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    }

    public static class StatusUpdateRequest {
        private Long appointmentId;
        private String newStatus;

        public Long getAppointmentId() { return appointmentId; }
        public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
        public String getNewStatus() { return newStatus; }
        public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    }

    public static class StatusUpdateResponse {
        private boolean success;
        private String message;
        private AppointmentDto data;

        public StatusUpdateResponse(boolean success, String message, AppointmentDto data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public AppointmentDto getData() { return data; }
        public void setData(AppointmentDto data) { this.data = data; }
    }
}

