package pin122.kursovaya.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import lombok.Data;
import pin122.kursovaya.dto.validation.OnCreate;
import pin122.kursovaya.dto.validation.OnUpdate;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * DTO профиля пациента для REST с вложенным {@link UserDto}; валидация зависит от групп создания и обновления.
 */
@Data
public class PatientDto {
    @NotNull(groups = OnUpdate.class)
    @Null(groups = OnCreate.class)
    private Long id;
    @NotNull(groups = {OnUpdate.class, OnCreate.class})
    private UserDto user;
    @NotNull(groups = {OnUpdate.class, OnCreate.class})
    @Past(message = "Дата рождения должна быть в прошлом", groups = {OnUpdate.class, OnCreate.class})
    private LocalDate birthDate;
    @NotNull(message = "Пол должен быть указан 1(муж), 2(жен)",groups = {OnUpdate.class, OnCreate.class})
    private Short gender; // 1 = male, 2 = female
    private String insuranceNumber;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public PatientDto() {
    }

    /**
     * @param id идентификатор пациента
     * @param user учётная запись
     * @param birthDate дата рождения
     * @param gender пол (коды как в доменной модели)
     * @param insuranceNumber номер полиса
     * @param createdAt время создания
     * @param updatedAt время обновления
     */
    public PatientDto(Long id, UserDto user, LocalDate birthDate, Short gender, String insuranceNumber, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.user = user;
        this.birthDate = birthDate;
        this.gender = gender;
        this.insuranceNumber = insuranceNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}