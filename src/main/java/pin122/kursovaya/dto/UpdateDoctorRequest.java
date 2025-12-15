package pin122.kursovaya.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * DTO для обновления данных врача с возможностью изменения специализаций
 */
@Data
public class UpdateDoctorRequest {
    
    @Valid
    private UserDto user;
    
    @NotBlank(message = "Отображаемое имя не может быть пустым")
    private String displayName;
    
    @Size(max = 50, message = "Описание должно содержать до 50 символов")
    private String bio;
    
    @Min(value = 0, message = "Опыт не может быть меньше 0")
    @Max(value = 80, message = "Опыт не может быть больше 80")
    private Integer experienceYears;
    
    private String photo; // Base64-кодированное изображение
    
    /**
     * Список ID специализаций для назначения врачу.
     * Если null - специализации не изменяются.
     * Если пустой список - все специализации удаляются.
     * Если содержит ID - устанавливаются указанные специализации.
     */
    private List<Long> specializationIds;
}

