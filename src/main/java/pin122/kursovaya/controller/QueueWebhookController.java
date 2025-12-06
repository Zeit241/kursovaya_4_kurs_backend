package pin122.kursovaya.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.dto.QueuePositionDto;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.service.QueueService;
import pin122.kursovaya.utils.ApiResponse;
import pin122.kursovaya.utils.SecurityUtils;

import java.util.List;
import java.util.Optional;

/**
 * Контроллер для работы с электронной очередью через вебхук
 * Все эндпоинты требуют авторизации по JWT токену
 */
@RestController
@RequestMapping("/api/queue/webhook")
public class QueueWebhookController {

    private final QueueService queueService;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;

    public QueueWebhookController(QueueService queueService,
                                  UserRepository userRepository,
                                  PatientRepository patientRepository) {
        this.queueService = queueService;
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * Инициализирует вебхук и автоматически строит очередь на основе appointments пользователя
     * Пропускает прошедшие записи (если сейчас на 20+ минут больше времени приема)
     * POST /api/queue/webhook/init
     */
    @PostMapping("/init")
    public ResponseEntity<ApiResponse<List<QueueEntryDto>>> initWebhook() {
        try {
            // Получаем текущего пользователя
            Optional<User> currentUser = SecurityUtils.getCurrentUser(userRepository);
            if (currentUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse<>("Пользователь не авторизован", 401));
            }

            // Получаем пациента текущего пользователя
            Optional<Patient> patient = patientRepository.findByUserId(currentUser.get().getId());
            if (patient.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>("Пользователь не является пациентом", 400));
            }

            // Автоматически строим очередь из appointments
            List<QueueEntryDto> queueEntries = queueService.buildQueueFromAppointments(patient.get().getId());

            return ResponseEntity.ok(new ApiResponse<>(
                    queueEntries.isEmpty() 
                        ? "Нет активных записей для построения очереди" 
                        : "Очередь успешно построена",
                    200,
                    queueEntries
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("Ошибка при инициализации вебхука: " + e.getMessage(), 500));
        }
    }

    /**
     * Получает позицию текущего пользователя в очереди к врачу
     * GET /api/queue/webhook/position?doctorId={doctorId}
     */
    @GetMapping("/position")
    public ResponseEntity<ApiResponse<QueuePositionDto>> getQueuePosition(@RequestParam Long doctorId) {
        try {
            // Получаем текущего пользователя
            Optional<User> currentUser = SecurityUtils.getCurrentUser(userRepository);
            if (currentUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse<>("Пользователь не авторизован", 401));
            }

            // Получаем пациента текущего пользователя
            Optional<Patient> patient = patientRepository.findByUserId(currentUser.get().getId());
            if (patient.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>("Пользователь не является пациентом", 400));
            }

            // Получаем позицию в очереди
            Optional<QueueEntryDto> queueEntry = queueService.getPatientQueuePosition(
                    patient.get().getId(),
                    doctorId
            );

            if (queueEntry.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>("Пользователь не находится в очереди к этому врачу", 404));
            }

            QueueEntryDto entry = queueEntry.get();
            boolean isNext = queueService.isPatientNextInQueue(patient.get().getId(), doctorId);

            QueuePositionDto positionDto = new QueuePositionDto(
                    entry.getId(),
                    entry.getDoctorId(),
                    entry.getPatientId(),
                    entry.getPosition(),
                    isNext,
                    isNext ? "Вы следующий в очереди" : "В очереди перед вами есть другие пациенты"
            );

            return ResponseEntity.ok(new ApiResponse<>(
                    "Позиция в очереди получена",
                    200,
                    positionDto
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("Ошибка при получении позиции: " + e.getMessage(), 500));
        }
    }

    /**
     * Проверяет, является ли текущий пользователь следующим в очереди
     * GET /api/queue/webhook/check-next?doctorId={doctorId}
     */
    @GetMapping("/check-next")
    public ResponseEntity<ApiResponse<QueuePositionDto>> checkIfNext(@RequestParam Long doctorId) {
        try {
            // Получаем текущего пользователя
            Optional<User> currentUser = SecurityUtils.getCurrentUser(userRepository);
            if (currentUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse<>("Пользователь не авторизован", 401));
            }

            // Получаем пациента текущего пользователя
            Optional<Patient> patient = patientRepository.findByUserId(currentUser.get().getId());
            if (patient.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>("Пользователь не является пациентом", 400));
            }

            // Проверяем, находится ли пользователь в очереди
            Optional<QueueEntryDto> queueEntry = queueService.getPatientQueuePosition(
                    patient.get().getId(),
                    doctorId
            );

            if (queueEntry.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>("Пользователь не находится в очереди к этому врачу", 404));
            }

            QueueEntryDto entry = queueEntry.get();
            boolean isNext = queueService.isPatientNextInQueue(patient.get().getId(), doctorId);

            QueuePositionDto positionDto = new QueuePositionDto(
                    entry.getId(),
                    entry.getDoctorId(),
                    entry.getPatientId(),
                    entry.getPosition(),
                    isNext,
                    isNext ? "Вы следующий в очереди" : "В очереди перед вами есть другие пациенты"
            );

            return ResponseEntity.ok(new ApiResponse<>(
                    isNext ? "Вы следующий в очереди" : "Вы не следующий в очереди",
                    200,
                    positionDto
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("Ошибка при проверке: " + e.getMessage(), 500));
        }
    }

    /**
     * Получает все очереди текущего пользователя
     * GET /api/queue/webhook/my-queues
     */
    @GetMapping("/my-queues")
    public ResponseEntity<ApiResponse<List<QueueEntryDto>>> getMyQueues() {
        try {
            // Получаем текущего пользователя
            Optional<User> currentUser = SecurityUtils.getCurrentUser(userRepository);
            if (currentUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse<>("Пользователь не авторизован", 401));
            }

            // Получаем пациента текущего пользователя
            Optional<Patient> patient = patientRepository.findByUserId(currentUser.get().getId());
            if (patient.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>("Пользователь не является пациентом", 400));
            }

            // Получаем все очереди пользователя
            List<QueueEntryDto> queues = queueService.getQueuesByPatient(patient.get().getId());

            return ResponseEntity.ok(new ApiResponse<>(
                    "Очереди получены",
                    200,
                    queues
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("Ошибка при получении очередей: " + e.getMessage(), 500));
        }
    }

    /**
     * Удаляет текущего пользователя из очереди к врачу
     * DELETE /api/queue/webhook/remove?doctorId={doctorId}
     */
    @DeleteMapping("/remove")
    public ResponseEntity<ApiResponse<Void>> removeFromQueue(@RequestParam Long doctorId) {
        try {
            // Получаем текущего пользователя
            Optional<User> currentUser = SecurityUtils.getCurrentUser(userRepository);
            if (currentUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse<>("Пользователь не авторизован", 401));
            }

            // Получаем пациента текущего пользователя
            Optional<Patient> patient = patientRepository.findByUserId(currentUser.get().getId());
            if (patient.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>("Пользователь не является пациентом", 400));
            }

            // Удаляем из очереди
            queueService.removePatientFromQueue(patient.get().getId(), doctorId);

            return ResponseEntity.ok(new ApiResponse<>(
                    "Пользователь успешно удален из очереди",
                    200,
                    null
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("Ошибка при удалении из очереди: " + e.getMessage(), 500));
        }
    }

    /**
     * Получает полную очередь к врачу (для просмотра)
     * GET /api/queue/webhook/doctor/{doctorId}
     */
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<List<QueueEntryDto>>> getDoctorQueue(@PathVariable Long doctorId) {
        try {
            List<QueueEntryDto> queue = queueService.getQueueByDoctor(doctorId);
            return ResponseEntity.ok(new ApiResponse<>(
                    "Очередь получена",
                    200,
                    queue
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("Ошибка при получении очереди: " + e.getMessage(), 500));
        }
    }
}

