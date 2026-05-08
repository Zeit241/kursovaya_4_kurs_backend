package pin122.kursovaya.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * Тело REST-запроса на создание врача вместе с учётной записью пользователя и начальным набором специализаций.
 */
@Data
public class CreateDoctorRequest {
    
    @NotNull(message = "Данные пользователя обязательны")
    @Valid
    private UserDto user;
    
    /** Устарело: не сохраняется в БД; в ответах API имя собирается из ФИО пользователя. */
    private String displayName;
    
    @Size(max = 50, message = "Описание должно содержать до 50 символов")
    private String bio;
    
    @Min(value = 0, message = "Опыт не может быть меньше 0")
    @Max(value = 80, message = "Опыт не может быть больше 80")
    private Integer experienceYears;
    
    /** UUID файла в Directus или абсолютный URL; не base64. */
    private String photo;
    
    /**
     * Список ID специализаций для назначения врачу
     */
    private List<Long> specializationIds;
}

