package pin122.kursovaya.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.UserRepository;

import java.util.Optional;

public class SecurityUtils {
    
    /**
     * Получает email текущего аутентифицированного пользователя
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
     * Получает объект User текущего аутентифицированного пользователя
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

