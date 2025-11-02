package pin122.kursovaya.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserDto {
    @Email(message = "Некорректный формат email")
    @NotBlank(message = "Email не может быть пустым")
    private String email;
    @NotBlank(message = "Телефон не может быть пустым")
    private String phone;
    @NotBlank(message = "Пароль не может быть пустым")
    private String password;
    @NotBlank(message = "Пароль не может быть пустым")
    private String confirmPassword;
    @NotBlank(message = "Имя не может быть пустым")
    private String fio;
}
