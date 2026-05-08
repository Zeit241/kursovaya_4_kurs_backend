package pin122.kursovaya.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ответ REST API после успешной аутентификации: JWT, роль, профиль текущего пользователя.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String message;
    private String roleCode;
    /** Полный профиль (как GET /api/users/me) */
    private CurrentUserDto user;
}
