package pin122.kursovaya.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "patients")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JsonBackReference
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "birth_date")
    private LocalDate birthDate;
    
    private Short gender; // 1 = male, 2 = female
    
    @Column(name = "insurance_number")
    private String insuranceNumber;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Patient(Long id, User user, LocalDate birthDate, Short gender, String insuranceNumber, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.user = user;
        this.birthDate = birthDate;
        this.gender = gender;
        this.insuranceNumber = insuranceNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Patient(LocalDate birthDate, Short gender, String insuranceNumber, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.birthDate = birthDate;
        this.gender = gender;
        this.insuranceNumber = insuranceNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Patient() {

    }
}