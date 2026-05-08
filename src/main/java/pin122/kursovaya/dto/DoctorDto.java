package pin122.kursovaya.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import pin122.kursovaya.dto.validation.OnCreate;
import pin122.kursovaya.dto.validation.OnUpdate;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO врача для REST: учётная запись ({@link UserDto}), профиль, рейтинг, список специализаций; используется при создании и обновлении с группами валидации.
 */
@Data
public class DoctorDto {
    @NotNull(groups = OnUpdate.class)
    @Null(groups = OnCreate.class)
    private Long id;
    @NotNull(groups = {OnCreate.class, OnUpdate.class})
    private UserDto user;
    private String displayName;
    @Size(min = 0, max = 50, message = "Описание должно содержать до 50 символов", groups = {OnCreate.class, OnUpdate.class})
    private String bio;
    @Min(value = 0, message = "Опыт не может быть меньше 0", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = 80, message = "Опыт не может быть больше 80", groups = {OnCreate.class, OnUpdate.class})
    private Integer experienceYears;
    /** Публичный URL ассета (ответ API); в БД — UUID Directus или URL. */
    private String photo;
    private Double rating; // Средний рейтинг из отзывов (1.0 - 5.0)
    private Integer reviewCount; // Количество отзывов
    private List<SpecializationDto> specializations; // Список специализаций врача
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public DoctorDto() {
    }

    /**
     * @param id идентификатор врача
     * @param user данные пользователя
     * @param displayName отображаемое имя (устаревшее поле в API)
     * @param bio краткое описание
     * @param experienceYears стаж в годах
     * @param photo публичный URL изображения врача
     * @param createdAt время создания
     * @param updatedAt время обновления
     */
    public DoctorDto(Long id, UserDto user, String displayName, String bio, Integer experienceYears, String photo, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.user = user;
        this.displayName = displayName;
        this.bio = bio;
        this.experienceYears = experienceYears;
        this.photo = photo;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * @param id идентификатор врача
     * @param user данные пользователя
     * @param displayName отображаемое имя (устаревшее поле в API)
     * @param bio краткое описание
     * @param experienceYears стаж в годах
     * @param photo публичный URL изображения врача
     * @param rating средний рейтинг по отзывам
     * @param reviewCount число отзывов
     * @param createdAt время создания
     * @param updatedAt время обновления
     */
    public DoctorDto(Long id, UserDto user, String displayName, String bio, Integer experienceYears, String photo, Double rating, Integer reviewCount, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.user = user;
        this.displayName = displayName;
        this.bio = bio;
        this.experienceYears = experienceYears;
        this.photo = photo;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}