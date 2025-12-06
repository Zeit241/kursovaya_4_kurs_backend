package pin122.kursovaya.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"passwordHash", "roles"})
    private User user;

    @ManyToOne
    @JoinColumn(name = "appointment_id")
    @JsonIgnoreProperties({"doctor", "patient", "createdBy"})
    private Appointment appointment;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    private OffsetDateTime sentAt;

    @Column(length = 20)
    private String status; // 'pending', 'sent', 'failed'

    public Notification() {
    }

    public Notification(User user, Appointment appointment, String type, Map<String, Object> payload, String status) {
        this.user = user;
        this.appointment = appointment;
        this.type = type;
        this.payload = payload;
        this.status = status;
    }
}


