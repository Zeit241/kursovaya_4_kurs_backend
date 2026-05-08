package pin122.kursovaya.dto;

import lombok.Data;
import pin122.kursovaya.model.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO медицинской услуги для REST (список, карточка, формы): цена, длительность, привязка к специализациям.
 */
@Data
public class ServiceDto {
    private Long id;
    private String name;
    private String code;
    private BigDecimal price;
    private Integer durationMinutes;
    private String description;
    /** Специализации, к которым привязана услуга (specialization_services) — для UI «категория» */
    private List<String> specializationNames = new ArrayList<>();

    /** ID специализаций (для форм редактирования в админке) */
    private List<Long> specializationIds = new ArrayList<>();

    public ServiceDto() {
    }

    /**
     * @param service сущность услуги; копируются только поля самой услуги, без списков специализаций
     */
    public ServiceDto(Service service) {
        this.id = service.getId();
        this.name = service.getName();
        this.code = service.getCode();
        this.price = service.getPrice();
        this.durationMinutes = service.getDurationMinutes();
        this.description = service.getDescription();
    }
}
