package pin122.kursovaya.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/**
 * JPA-сущность связи «специализация — услуга» (таблица {@code specialization_services}) с составным ключом и признаком активности.
 */
@Entity
@Data
@Table(name = "specialization_services")
public class SpecializationServiceLink {

    @EmbeddedId
    private SpecializationServiceLinkPk id;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
