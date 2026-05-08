package pin122.kursovaya.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.UserRepository;

import java.util.Optional;

/**
 * Вспомогательные методы доступа к данным текущего пользователя из {@link SecurityContextHolder}.
 */
public class SecurityUtils {

    /**
     * Возвращает email (логин) текущего аутентифицированного пользователя.
     *
     * @return email из {@link UserDetails#getUsername()}, либо пустой {@link Optional}, если контекст безопасности
     *         не содержит {@link UserDetails}
     */
    public static Optional<String> getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return Optional.of(userDetails.getUsername());
        }
        return Optional.empty();
    }

    /**
     * Загружает сущность {@link User} по email текущего аутентифицированного пользователя.
     *
     * @param userRepository репозиторий для поиска пользователя по email
     * @return {@link User}, если аутентификация есть и пользователь найден в БД; иначе пустой {@link Optional}
     */
    public static Optional<User> getCurrentUser(UserRepository userRepository) {
        Optional<String> emailOpt = getCurrentUserEmail();
        if (emailOpt.isEmpty()) {
            return Optional.empty();
        }
        User user = userRepository.findByEmail(emailOpt.get());
        return user != null ? Optional.of(user) : Optional.empty();
    }
}
