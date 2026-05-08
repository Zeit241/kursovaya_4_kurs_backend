package pin122.kursovaya.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.dto.WebSocketSessionData;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.service.RedisQueueService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Слушатель событий WebSocket (подключение и отключение STOMP-сессии).
 * <p>
 * При подключении аутентифицированного пациента строит очередь на текущий день в {@link RedisQueueService}, сохраняет
 * сессию в Redis и отправляет ответ в персональную очередь пользователя. При отключении удаляет сессию и при необходимости
 * очищает очереди. Соответствие STOMP session id и Redis session id хранится в памяти процесса.
 *
 * @see RedisQueueService
 * @see SessionConnectedEvent
 * @see SessionDisconnectEvent
 */
@Component
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisQueueService redisQueueService;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    
    // Маппинг STOMP session ID -> наш Redis session ID
    private final Map<String, String> stompToRedisSessionMap = new ConcurrentHashMap<>();

    /**
     * @param messagingTemplate   отправка сообщений пользователю по STOMP
     * @param redisQueueService   очереди и сессии в Redis
     * @param userRepository      поиск пользователя по email из {@link Authentication}
     * @param patientRepository   проверка, что пользователь является пациентом
     */
    public WebSocketEventListener(SimpMessagingTemplate messagingTemplate,
                                  RedisQueueService redisQueueService,
                                  UserRepository userRepository,
                                  PatientRepository patientRepository) {
        this.messagingTemplate = messagingTemplate;
        this.redisQueueService = redisQueueService;
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * Обрабатывает успешное подключение WebSocket: генерирует идентификатор сессии Redis, строит очередь на сегодня,
     * сохраняет {@link WebSocketSessionData}, рассылает {@link RedisQueueService.QueueInitResponse} в {@code /queue/user}.
     *
     * @param event событие подключения STOMP с данными пользователя
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Authentication authentication = (Authentication) headerAccessor.getUser();
        String stompSessionId = headerAccessor.getSessionId();
        
        if (authentication == null) {
            System.out.println("DEBUG WebSocket: Подключение без аутентификации, пропускаем");
            return;
        }
        
        String email = authentication.getName();
        System.out.println("DEBUG WebSocket: Новое подключение от пользователя: " + email + 
                ", STOMP session: " + stompSessionId);
        
        try {
            User user = userRepository.findByEmail(email);
            
            if (user == null) {
                System.out.println("DEBUG WebSocket: Пользователь не найден: " + email);
                return;
            }
            
            Optional<Patient> patientOpt = patientRepository.findByUserId(user.getId());
            
            if (patientOpt.isEmpty()) {
                System.out.println("DEBUG WebSocket: Пользователь не является пациентом, пропускаем инициализацию");
                return;
            }
            
            Patient patient = patientOpt.get();
            
            // Генерируем уникальный sessionId для Redis
            String redisSessionId = redisQueueService.generateSessionId();
            
            // Сохраняем маппинг STOMP -> Redis session
            if (stompSessionId != null) {
                stompToRedisSessionMap.put(stompSessionId, redisSessionId);
            }
            
            System.out.println("DEBUG WebSocket: Создан Redis session: " + redisSessionId + 
                    " для пациента: " + patient.getId());
            
            // Формируем очередь на текущий день
            List<QueueEntryDto> queueEntries = redisQueueService.buildQueueForToday(patient.getId());
            
            // Собираем ID appointments для сохранения в сессии
            List<Long> appointmentIds = queueEntries.stream()
                    .map(QueueEntryDto::getAppointmentId)
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
            
            // Создаем и сохраняем данные сессии в Redis
            WebSocketSessionData sessionData = new WebSocketSessionData(
                    redisSessionId,
                    user.getId(),
                    patient.getId(),
                    email,
                    appointmentIds,
                    LocalDateTime.now()
            );
            redisQueueService.saveSession(redisSessionId, sessionData);
            
            // Отправляем ответ клиенту
            messagingTemplate.convertAndSendToUser(
                email,
                "/queue/user",
                new RedisQueueService.QueueInitResponse(
                    true,
                    queueEntries.isEmpty() 
                        ? "Нет активных записей на сегодня" 
                        : "Очередь на сегодня успешно построена",
                    queueEntries
                )
            );
            
            System.out.println("DEBUG WebSocket: Инициализация завершена, записей в очереди: " + 
                    queueEntries.size());
            
        } catch (Exception e) {
            System.err.println("DEBUG WebSocket: Ошибка при инициализации: " + e.getMessage());
            e.printStackTrace();
            
            // Отправляем сообщение об ошибке клиенту
            messagingTemplate.convertAndSendToUser(
                email,
                "/queue/user",
                new RedisQueueService.QueueInitResponse(
                    false,
                    "Ошибка при инициализации очереди: " + e.getMessage(),
                    null
                )
            );
        }
    }

    /**
     * Обрабатывает отключение WebSocket: по STOMP session id находит сессию Redis и удаляет её через {@link RedisQueueService#deleteSession}.
     *
     * @param event событие отключения STOMP
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String stompSessionId = headerAccessor.getSessionId();
        String userName = headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : "unknown";
        
        System.out.println("DEBUG WebSocket: Отключение пользователя: " + userName + 
                ", STOMP session: " + stompSessionId);
        
        // Получаем Redis sessionId из маппинга
        String redisSessionId = stompToRedisSessionMap.remove(stompSessionId);
        
        if (redisSessionId != null) {
            // Удаляем сессию из Redis (включая очистку очередей если это последняя сессия пациента)
            redisQueueService.deleteSession(redisSessionId);
            System.out.println("DEBUG WebSocket: Redis session удалена: " + redisSessionId);
        } else {
            System.out.println("DEBUG WebSocket: Redis session не найдена для STOMP session: " + stompSessionId);
        }
    }
    
    /**
     * Возвращает идентификатор сессии Redis по идентификатору STOMP-сессии.
     *
     * @param stompSessionId идентификатор сессии STOMP
     * @return идентификатор сессии в Redis или {@code null}, если маппинг отсутствует
     */
    public String getRedisSessionId(String stompSessionId) {
        return stompToRedisSessionMap.get(stompSessionId);
    }
}
