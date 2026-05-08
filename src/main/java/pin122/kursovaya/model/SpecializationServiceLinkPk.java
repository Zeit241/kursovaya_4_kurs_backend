package pin122.kursovaya.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Встраиваемый составной первичный ключ для связи специализации и услуги.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecializationServiceLinkPk implements Serializable {

    @Column(name = "specialization_id", nullable = false)
    private Long specializationId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;
}
