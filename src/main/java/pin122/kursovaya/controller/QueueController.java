package pin122.kursovaya.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.QueueEntry;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.service.QueueService;
import pin122.kursovaya.service.RedisQueueService;
import pin122.kursovaya.utils.SecurityUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST-контроллер очередей: классическая очередь через {@link QueueService} и «живая» очередь из Redis для врачей.
 * <p>
 * Базовый путь: {@code /api/queue}.
 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;
    private final RedisQueueService redisQueueService;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;

    /**
     * @param queueService        сервис очередей (БД)
     * @param redisQueueService   очереди в Redis
     * @param userRepository      проверка текущего пользователя и роли
     * @param doctorRepository    сопоставление пользователя с врачом
     */
    public QueueController(QueueService queueService,
                          RedisQueueService redisQueueService,
                          UserRepository userRepository,
                          DoctorRepository doctorRepository) {
        this.queueService = queueService;
        this.redisQueueService = redisQueueService;
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
    }

    /**
     * Возвращает очередь к врачу из сервиса на основе БД.
     *
     * @param doctorId идентификатор врача
     * @return HTTP 200 и список {@link QueueEntryDto}
     */
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<QueueEntryDto>> getQueueByDoctor(@PathVariable Long doctorId) {
        return ResponseEntity.ok(queueService.getQueueByDoctor(doctorId));
    }

    /**
     * Возвращает актуальную очередь из Redis после пересчёта; доступ только администратору или врачу «своего» {@code doctorId}.
     *
     * @param doctorId идентификатор врача
     * @param date     дата очереди (по умолчанию сегодня)
     * @return HTTP 200 и список позиций; HTTP 401/403 при отсутствии прав
     */
    @GetMapping("/live/doctor/{doctorId}")
    public ResponseEntity<?> getLiveQueueByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Optional<User> userOpt = SecurityUtils.getCurrentUser(userRepository);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Не авторизован"));
        }
        User user = userOpt.get();
        boolean admin = user.getRole() != null && "admin".equalsIgnoreCase(user.getRole().getCode());
        if (!admin) {
            if (user.getRole() == null || !"doctor".equalsIgnoreCase(user.getRole().getCode())) {
                return ResponseEntity.status(403).body(Map.of("error", "Нет доступа"));
            }
            Optional<Doctor> doctorOpt = doctorRepository.findByUserId(user.getId());
            if (doctorOpt.isEmpty() || !doctorOpt.get().getId().equals(doctorId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Нет доступа к очереди этого врача"));
            }
        }
        LocalDate queueDate = date != null ? date : LocalDate.now();
        redisQueueService.recalculateQueueForDoctor(doctorId, queueDate);
        return ResponseEntity.ok(redisQueueService.getQueueByDoctor(doctorId, queueDate));
    }

    /**
     * Возвращает одну позицию очереди по идентификатору.
     *
     * @param id первичный ключ записи очереди
     * @return HTTP 200 и DTO или HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<QueueEntryDto> getById(@PathVariable Long id) {
        return queueService.getQueueEntryById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Добавляет запись в очередь (сохранение сущности {@link QueueEntry}).
     *
     * @param entry данные позиции очереди
     * @return HTTP 200 и сохранённый DTO
     */
    @PostMapping
    public ResponseEntity<QueueEntryDto> addToQueue(@RequestBody QueueEntry entry) {
        return ResponseEntity.ok(queueService.saveQueueEntry(entry));
    }

    /**
     * Удаляет позицию из очереди по идентификатору.
     *
     * @param id идентификатор записи очереди
     * @return HTTP 204 при успехе
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeFromQueue(@PathVariable Long id) {
        queueService.deleteQueueEntry(id);
        return ResponseEntity.noContent().build();
    }
}
