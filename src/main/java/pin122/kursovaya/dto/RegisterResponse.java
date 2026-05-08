package pin122.kursovaya.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ответ REST API после регистрации пользователя без профиля пациента: JWT и {@link UserDto}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private String token;
    private UserDto user;
    private String message;
}

