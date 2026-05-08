package pin122.kursovaya.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * JPA-сущность справочника диагнозов (МКБ или внутренний код, название, категория).
 */
@Entity
@Data
@Table(name = "diagnoses")
public class Diagnosis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String category;
}
