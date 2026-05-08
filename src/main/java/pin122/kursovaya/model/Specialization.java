package pin122.kursovaya.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * JPA-сущность справочника медицинских специализаций.
 */
@Entity
@Data
@Table(name = "specializations")
public class Specialization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;
}