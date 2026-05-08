package pin122.kursovaya.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * JPA-сущность платной медицинской услуги: название, код, цена, длительность, описание.
 */
@Entity
@Data
@Table(name = "services")
public class Service {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String code;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 30;

    private String description;
}
