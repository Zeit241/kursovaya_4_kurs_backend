package pin122.kursovaya.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.dto.QueuePositionDto;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.service.RedisQueueService;
import pin122.kursovaya.utils.SecurityUtils;

import java.util.List;
import java.util.Optional;

/**
 * WebSocket контроллер для работы с электронной очередью
 * Авторизация через JWT токен при подключении
 */
@Controller
public class QueueWebSocketController {

    private final RedisQueueService redisQueueService;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public QueueWebSocketController(RedisQueueService redisQueueService,
                                    UserRepository userRepository,
                                    PatientRepository patientRepository,
                                    SimpMessagingTemplate messagingTemplate) {
        this.redisQueueService = redisQueueService;
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Обработка подключения - автоматически строит очередь
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
            // Автоматически строим очередь из appointments в Redis
            redisQueueService.buildQueueFromAppointments(patient.get().getId());
            // Получаем все очереди пациента
            List<QueueEntryDto> queueEntries = redisQueueService.getQueuesByPatient(patient.get().getId());
            System.out.println("DEBUG WebSocket: Построено записей в очереди: " + queueEntries.size());

            messagingTemplate.convertAndSendToUser(
                email,
                "/queue/user",
                new QueueInitResponse(true, 
                    queueEntries.isEmpty() 
                        ? "Нет активных записей для построения очереди" 
                        : "Очередь успешно построена",
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
}

