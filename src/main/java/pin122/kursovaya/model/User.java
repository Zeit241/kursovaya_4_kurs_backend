package pin122.kursovaya.model;

import jakarta.persistence.*;
import lombok.Data;
import pin122.kursovaya.dto.UserDto;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true)
    private String phone;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private String middleName;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "is_active")
    private boolean active = true;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Patient patient;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Doctor doctor;

    @ManyToMany
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

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