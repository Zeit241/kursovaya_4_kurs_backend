package pin122.kursovaya.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Запрос на отправку учётных данных для входа на email пользователя (админ после регистрации).
 */
@Data
public class SendLoginCredentialsRequest {

    @NotBlank(message = "Email не может быть пустым")
    @Email(message = "Некорректный формат email")
    private String email;

    @NotBlank(message = "Пароль не может быть пустым")
    private String password;

    private String recipientName;
}
