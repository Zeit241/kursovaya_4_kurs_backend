package pin122.kursovaya.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import pin122.kursovaya.controller.QueueWebSocketController;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.service.QueueService;
import pin122.kursovaya.dto.QueueEntryDto;

import java.util.List;
import java.util.Optional;

@Component
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final QueueService queueService;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;

    public WebSocketEventListener(SimpMessagingTemplate messagingTemplate,
                                  QueueService queueService,
                                  UserRepository userRepository,
                                  PatientRepository patientRepository) {
        this.messagingTemplate = messagingTemplate;
        this.queueService = queueService;
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Authentication authentication = (Authentication) headerAccessor.getUser();
        
        if (authentication != null) {
            String email = authentication.getName();
            System.out.println("DEBUG WebSocket: Новое подключение от пользователя: " + email);
            
            try {
                User user = userRepository.findByEmail(email);
                
                if (user != null) {
                    Optional<Patient> patient = patientRepository.findByUserId(user.getId());
                    if (patient.isPresent()) {
                        System.out.println("DEBUG WebSocket: Автоматическая инициализация очереди для пациента: " + patient.get().getId());
                        // Автоматически строим очередь при подключении
                        List<QueueEntryDto> queueEntries = queueService.buildQueueFromAppointments(patient.get().getId());
                        
                        messagingTemplate.convertAndSendToUser(
                            email,
                            "/queue/user",
                            new QueueWebSocketController.QueueInitResponse(
                                true,
                                queueEntries.isEmpty() 
                                    ? "Нет активных записей для построения очереди" 
                                    : "Очередь успешно построена при подключении",
                                queueEntries
                            )
                        );
                        System.out.println("DEBUG WebSocket: Автоматическая инициализация завершена, создано записей: " + queueEntries.size());
                    } else {
                        System.out.println("DEBUG WebSocket: Пользователь не является пациентом, пропускаем инициализацию");
                    }
                }
            } catch (Exception e) {
                System.err.println("DEBUG WebSocket: Ошибка при автоматической инициализации: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        System.out.println("DEBUG WebSocket: Отключение пользователя: " + 
            (headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : "unknown"));
    }
}

