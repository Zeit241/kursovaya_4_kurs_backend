package pin122.kursovaya.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.Map;

@Data
@Entity
@Table(name = "patients")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private LocalDate birthDate;
    private Short gender; // 1 = male, 2 = female
    private String insuranceNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> emergencyContact;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Patient(Long id, User user, LocalDate birthDate, Short gender, String insuranceNumber, Map<String, Object> emergencyContact, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.user = user;
        this.birthDate = birthDate;
        this.gender = gender;
        this.insuranceNumber = insuranceNumber;
        this.emergencyContact = emergencyContact;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Patient(LocalDate birthDate, Short gender, String insuranceNumber, Map<String, Object> emergencyContact, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.birthDate = birthDate;
        this.gender = gender;
        this.insuranceNumber = insuranceNumber;
        this.emergencyContact = emergencyContact;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Patient() {

    }
}