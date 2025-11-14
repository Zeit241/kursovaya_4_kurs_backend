package pin122.kursovaya.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class CreateUserWithPatientDto {
    // Данные пользователя
    @Email(message = "Некорректный формат email")
    @NotBlank(message = "Email не может быть пустым")
    private String email;
    
    @NotBlank(message = "Телефон не может быть пустым")
    private String phone;
    
    @NotBlank(message = "Пароль не может быть пустым")
    private String password;
    
    @NotBlank(message = "Подтверждение пароля не может быть пустым")
    private String confirmPassword;
    
    @NotBlank(message = "ФИО не может быть пустым")
    private String fio;
    
    // Данные пациента
    @NotNull(message = "Дата рождения обязательна")
    @Past(message = "Дата рождения должна быть в прошлом")
    private LocalDate birthDate;
    
    @NotNull(message = "Пол должен быть указан (1 - мужской, 2 - женский)")
    private Short gender; // 1 = male, 2 = female
    
    private String insuranceNumber; // Опциональное поле
    
    private Map<String, Object> emergencyContact; // Опциональное поле
}

