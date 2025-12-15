package pin122.kursovaya.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * DTO для создания нового врача с возможностью указания специализаций
 */
@Data
public class CreateDoctorRequest {
    
    @NotNull(message = "Данные пользователя обязательны")
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
     * Список ID специализаций для назначения врачу
     */
    private List<Long> specializationIds;
}

