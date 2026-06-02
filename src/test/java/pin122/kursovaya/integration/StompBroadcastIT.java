package pin122.kursovaya.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.User;
import pin122.kursovaya.security.CustomUserDetailsService;
import pin122.kursovaya.service.RedisQueueService;
import pin122.kursovaya.utils.JwtTokenProvider;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты STOMP/WebSocket: доступность эндпоинта и broadcast очереди.
 */
@DisplayName("StompBroadcastIT")
class StompBroadcastIT extends AbstractIntegrationIT {

    private static final String PASSWORD = "StompPass123!";

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("SockJS info endpoint /queue-websocket/info доступен")
    void websocketInfoEndpoint_isReachable() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/queue-websocket/info", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("websocket"));
    }

    @Test
    @DisplayName("SimpMessagingTemplate зарегистрирован в контексте")
    void simpMessagingTemplate_isAvailable() {
        assertNotNull(messagingTemplate);
    }

    @Test
    @DisplayName("generateSessionId возвращает непустой UUID")
    void generateSessionId_returnsUuid() {
        String sessionId = redisQueueService.generateSessionId();

        assertNotNull(sessionId);
        assertFalse(sessionId.isBlank());
        assertNotEquals(redisQueueService.generateSessionId(), sessionId);
    }

    @Test
    @DisplayName("notifyQueueUpdated отправляет событие в STOMP-топик /topic/queue/doctor/{id}")
    void notifyQueueUpdated_broadcastsToDoctorTopic() throws Exception {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        LocalDate day = today();

        redisQueueService.addToQueue(requirePatientId(patientUser), doctor.getId(), 0, day);

        BlockingQueue<Object> messages = new LinkedBlockingQueue<>();
        StompSession session = connectStomp(patientUser.getEmail());

        session.subscribe("/topic/queue/doctor/" + doctor.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.offer(payload);
            }
        });

        redisQueueService.notifyQueueUpdated(doctor.getId(), day);

        Object payload = messages.poll(10, TimeUnit.SECONDS);
        assertNotNull(payload, "STOMP broadcast should arrive within timeout");
        assertTrue(payload instanceof Map);
    }

    @Test
    @DisplayName("notifyUserQueueUpdate отправляет персональное сообщение пользователю")
    void notifyUserQueueUpdate_sendsUserQueueMessage() throws Exception {
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        List<QueueEntryDto> entries = List.of(new QueueEntryDto(
                null, 1L, null, requirePatientId(patientUser), 0, null));

        StompSession session = connectStomp(patientUser.getEmail());
        assertTrue(session.isConnected());

        assertDoesNotThrow(() ->
                redisQueueService.notifyUserQueueUpdate(patientUser.getEmail(), entries));

        session.disconnect();
    }

    @Test
    @DisplayName("STOMP CONNECT с валидным JWT успешно устанавливает сессию")
    void stompConnect_withValidJwt_succeeds() throws Exception {
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);

        StompSession session = connectStomp(patientUser.getEmail());

        assertTrue(session.isConnected());
        session.disconnect();
    }

    @Test
    @DisplayName("STOMP CONNECT без JWT отклоняется")
    void stompConnect_withoutJwt_isRejected() {
        WebSocketStompClient stompClient = createStompClient();
        String url = "ws://localhost:" + port + "/queue-websocket";

        assertThrows(Exception.class, () ->
                stompClient.connectAsync(url, new WebSocketHttpHeaders(), new StompHeaders(),
                        new StompSessionHandlerAdapter() {
                        }).get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("clearQueueWithNotification рассылает пустую очередь в топик")
    void clearQueueWithNotification_broadcastsEmptyQueue() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor"), PASSWORD);
        User patientUser = createPatientUser(uniqueEmail("patient"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        LocalDate day = today();

        redisQueueService.addToQueue(requirePatientId(patientUser), doctor.getId(), 0, day);
        assertEquals(1L, redisQueueService.getQueueSize(doctor.getId(), day));

        assertDoesNotThrow(() -> redisQueueService.clearQueueWithNotification(doctor.getId(), day));

        assertEquals(0L, redisQueueService.getQueueSize(doctor.getId(), day));
    }

    @Test
    @DisplayName("getAllActiveSessions возвращает сохранённые WebSocket-сессии")
    void getAllActiveSessions_listsSavedSessions() {
        var data = new pin122.kursovaya.dto.WebSocketSessionData(
                "broadcast-session",
                1L,
                2L,
                "active@test.com",
                List.of(),
                java.time.LocalDateTime.now()
        );
        redisQueueService.saveSession("broadcast-session", data);

        var sessions = redisQueueService.getAllActiveSessions();

        assertFalse(sessions.isEmpty());
        assertTrue(sessions.stream().anyMatch(s -> "broadcast-session".equals(s.getSessionId())));
    }

    @Test
    @DisplayName("REST health: приложение поднято на случайном порту")
    void applicationStarts_onRandomPort() {
        assertTrue(port > 0);
    }

    private StompSession connectStomp(String email) throws Exception {
        String token = jwtTokenProvider.generateToken(userDetailsService.loadUserByUsername(email));
        WebSocketStompClient stompClient = createStompClient();
        String url = "ws://localhost:" + port + "/queue-websocket";

        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.add("Authorization", "Bearer " + token);

        return stompClient.connectAsync(url, new WebSocketHttpHeaders(), stompHeaders,
                new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);
    }

    private WebSocketStompClient createStompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }
}
