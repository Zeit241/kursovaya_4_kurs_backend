package pin122.kursovaya.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import pin122.kursovaya.utils.FormatUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JPA-сущность профиля врача: связь с {@link User}, биография, стаж, фото, коллекции приёмов и привязок к специализациям.
 */
@Data
@Entity
@Table(name = "doctors")
@EqualsAndHashCode(exclude = {"user", "appointments", "specializations"})
public class Doctor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JsonBackReference
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    private Boolean hide;

    private String bio;

    /**
     * Отображаемое имя врача, вычисляемое из ФИО связанного пользователя (не сохраняется в БД).
     *
     * @return строка для интерфейса или отчётов
     */
    @Transient
    public String getDisplayName() {
        return FormatUtils.doctorDisplayName(user);
    }
    
    @Column(name = "experience_years")
    private Integer experienceYears;
    
    /**
     * UUID файла в Directus ({@code directus_files.id}) или абсолютный URL к ассету; бинарное хранение не используется.
     */
    @Column(name = "photo", length = 1024)
    private String photo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Appointment> appointments = new HashSet<>();

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DoctorSpecialization> specializations = new ArrayList<>();
    // Getters, Setters
}