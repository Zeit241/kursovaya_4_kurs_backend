package pin122.kursovaya.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "doctor_schedules")
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    private Integer roomId;
    private Short weekday; // 0-6
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer slotDurationMinutes;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
