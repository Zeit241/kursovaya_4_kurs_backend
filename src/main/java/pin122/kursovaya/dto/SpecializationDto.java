package pin122.kursovaya.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pin122.kursovaya.dto.validation.OnCreate;
import pin122.kursovaya.dto.validation.OnUpdate;
import pin122.kursovaya.model.Specialization;

/**
 * DTO справочника медицинских специализаций для REST и вложенного использования (код, название, описание).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecializationDto {
    @NotNull(groups = OnUpdate.class)
    @Null(groups = OnCreate.class)
    private Long id;

    @NotBlank(message = "Код специализации не может быть пустым", groups = {OnCreate.class, OnUpdate.class})
    private String code;

    @NotBlank(message = "Название специализации не может быть пустым", groups = {OnCreate.class, OnUpdate.class})
    private String name;

    private String description;

    /**
     * @param specialization сущность специализации
     */
    public SpecializationDto(Specialization specialization) {
        this.id = specialization.getId();
        this.code = specialization.getCode();
        this.name = specialization.getName();
        this.description = specialization.getDescription();
    }
}

