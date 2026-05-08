package pin122.kursovaya.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * JPA-сущность приёма (записи на слот): расписание, врач, пациент, кабинет, услуга, диагноз, статус и клинические текстовые поля.
 */
@Data
@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "schedule_id", nullable = true)
    @JsonIgnoreProperties({"doctor", "room"})
    private Schedule schedule;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    @JsonIgnoreProperties({"appointments", "specializations"})
    private Doctor doctor;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = true)
    @JsonIgnoreProperties({"user"})
    private Patient patient;

    @ManyToOne
    @JoinColumn(name = "room_id")
    @JsonIgnoreProperties({"schedules"})
    private Room room;

    @ManyToOne
    @JoinColumn(name = "service_id")
    @JsonIgnoreProperties({"appointments"})
    private Service service;

    @ManyToOne
    @JoinColumn(name = "diagnosis_id")
    @JsonIgnoreProperties({"appointments"})
    private Diagnosis diagnosis;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Column(nullable = false)
    private String status; // enum preferred

    @Column(nullable = false)
    private String source;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(columnDefinition = "TEXT")
    private String complaints;

    @Column(columnDefinition = "TEXT")
    private String anamnesis;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    // Getters, Setters
}