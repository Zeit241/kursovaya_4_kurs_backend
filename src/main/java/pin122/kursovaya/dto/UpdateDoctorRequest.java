package pin122.kursovaya.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * Тело REST-запроса на обновление профиля врача и, при необходимости, списка специализаций (по идентификаторам).
 */
@Data
public class UpdateDoctorRequest {
    
    @Valid
    private UserDto user;
    
    /** Устарело: не сохраняется в БД; меняйте ФИО в блоке user. */
    private String displayName;
    
    private String bio;
    
    @Min(value = 0, message = "Опыт не может быть меньше 0")
    @Max(value = 80, message = "Опыт не может быть больше 80")
    private Integer experienceYears;
    
    /** UUID файла в Directus или абсолютный URL; не base64. */
    private String photo;
    
    /**
     * Список ID специализаций для назначения врачу.
     * Если null - специализации не изменяются.
     * Если пустой список - все специализации удаляются.
     * Если содержит ID - устанавливаются указанные специализации.
     */
    private List<Long> specializationIds;
}

