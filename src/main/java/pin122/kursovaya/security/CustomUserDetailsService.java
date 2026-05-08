package pin122.kursovaya.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pin122.kursovaya.model.Role;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.UserRepository;

import java.util.Collection;
import java.util.List;

/**
 * Реализация {@link UserDetailsService} для Spring Security: загрузка пользователя по email и формирование ролей.
 *
 * @see UserRepository
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * @param userRepository репозиторий пользователей
     */
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Загружает активного пользователя по адресу email для аутентификации.
     *
     * @param email адрес электронной почты (логин)
     * @return {@link UserDetails} с паролем, флагом активности и authority {@code ROLE_*} по коду роли
     * @throws UsernameNotFoundException если пользователь с таким email не найден
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }
        
        // Инициализируем роль в рамках транзакции
        if (user.getRole() != null) {
            user.getRole().getCode(); // Это загрузит роль
        }
        
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                user.isActive(),
                true,
                true,
                true,
                getAuthorities(user)
        );
    }

    /**
     * Формирует коллекцию прав Spring Security на основе роли пользователя.
     *
     * @param user сущность пользователя из БД
     * @return список из одного {@link SimpleGrantedAuthority} с префиксом {@code ROLE_} или пустой список, если роли нет
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        if (user.getRole() != null) {
            String roleCode = user.getRole().getCode();
            return List.of(new SimpleGrantedAuthority("ROLE_" + roleCode.toUpperCase()));
        }
        return List.of();
    }
}

