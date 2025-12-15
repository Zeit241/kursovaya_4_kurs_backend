package pin122.kursovaya.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO для хранения данных WebSocket сессии в Redis
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
