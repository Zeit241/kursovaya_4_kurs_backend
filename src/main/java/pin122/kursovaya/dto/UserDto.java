package pin122.kursovaya.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Data;
import pin122.kursovaya.dto.validation.OnCreate;
import pin122.kursovaya.dto.validation.OnUpdate;
import pin122.kursovaya.model.User;

import java.time.OffsetDateTime;

/**
 * DTO учётной записи пользователя для REST и вложенного использования в профилях врача/пациента; валидация зависит от групп {@link pin122.kursovaya.dto.validation.OnCreate} и {@link pin122.kursovaya.dto.validation.OnUpdate}.
 */
@Data
public class UserDto {
    @NotNull(groups = OnUpdate.class)
    @Null(groups = OnCreate.class)
    private Long id;
    @Email(message = "Некорректный формат email",groups = {OnCreate.class, OnUpdate.class})
    @NotBlank(message = "Email не может быть пустым",groups = {OnCreate.class, OnUpdate.class})
    private String email;
    private String phone;
    @NotBlank(message = "Имя не может быть пустым",groups = {OnCreate.class, OnUpdate.class})
    private String firstName;
    @NotBlank(message = "Фамилия не может быть пустой",groups = {OnCreate.class, OnUpdate.class})
    private String lastName;
    private String middleName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private boolean active;
    private String roleCode; // Для обратной совместимости

    public UserDto() {
    }

    /**
     * @param id идентификатор пользователя
     * @param email электронная почта
     * @param phone телефон
     * @param firstName имя
     * @param lastName фамилия
     * @param middleName отчество
     * @param createdAt время создания
     * @param updatedAt время обновления
     * @param active признак активности учётной записи
     */
    public UserDto(Long id, String email, String phone, String firstName, String lastName, String middleName, OffsetDateTime createdAt, OffsetDateTime updatedAt, boolean active) {
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.firstName = firstName;
        this.lastName = lastName;
        this.middleName = middleName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.active = active;
    }

    /**
     * @param user сущность пользователя; {@code roleCode} заполняется из связанной роли при её наличии
     */
    public UserDto(User user) {
        this.id = user.getId();
        this.email =user.getEmail();
        this.phone = user.getPhone();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.middleName = user.getMiddleName();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
        this.active = user.isActive();
        this.roleCode = user.getRole() != null ? user.getRole().getCode() : null;
    }
}