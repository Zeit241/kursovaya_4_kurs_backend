package pin122.kursovaya.model;
import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * JPA-сущность отзыва пациента о приёме: уникальная связь с приёмом, оценка и текст.
 */
@Data
@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false)
    private Short rating; // 1-5

    @Column(name = "review_text")
    private String reviewText;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Getters, Setters
}