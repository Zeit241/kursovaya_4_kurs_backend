package pin122.kursovaya.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Перенос данных WebSocket-сессии при хранении в Redis: пользователь, связанные приёмы, момент подключения.
 */
@Data
public class WebSocketSessionData {
    private String sessionId;
    private Long userId;
    private Long patientId;
    private String email;
    private List<Long> appointmentIds;
    private LocalDateTime connectedAt;

    public WebSocketSessionData() {
    }

    /**
     * @param sessionId идентификатор сессии
     * @param userId идентификатор пользователя
     * @param patientId идентификатор пациента, если применимо
     * @param email email пользователя
     * @param appointmentIds идентификаторы приёмов, по которым подписка активна
     * @param connectedAt время установления соединения
     */
    public WebSocketSessionData(String sessionId, Long userId, Long patientId, String email, 
                                 List<Long> appointmentIds, LocalDateTime connectedAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.patientId = patientId;
        this.email = email;
        this.appointmentIds = appointmentIds;
        this.connectedAt = connectedAt;
    }
}
