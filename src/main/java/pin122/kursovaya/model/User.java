package pin122.kursovaya.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pin122.kursovaya.dto.UserDto;

import java.time.OffsetDateTime;

/**
 * JPA-сущность учётной записи: контакты, хэш пароля, ФИО, роль, опциональные профили {@link Patient} и {@link Doctor}.
 */
@Data
@Entity
@Table(name = "users")
@EqualsAndHashCode(exclude = {"patient", "doctor", "role"})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true)
    private String phone;
    
    @Column(name = "password_hash")
    private String passwordHash;
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "is_active")
    private boolean active = true;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonManagedReference
    private Patient patient;


    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonManagedReference
    private Doctor doctor;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    /**
     * @param userDto DTO с полями пользователя; временные метки создания и обновления выставляются текущим моментом
     */
    public User(UserDto userDto) {
        id = userDto.getId();
        email = userDto.getEmail();
        phone = userDto.getPhone();
        firstName = userDto.getFirstName();
        lastName = userDto.getLastName();
        middleName = userDto.getMiddleName();
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    public User() {
    }
}