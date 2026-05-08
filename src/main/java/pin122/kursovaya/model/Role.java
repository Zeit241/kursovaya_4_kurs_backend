package pin122.kursovaya.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * JPA-сущность роли пользователя в системе (код и отображаемое имя).
 */
@Data
@Entity
@Table(name = "roles")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;
}



