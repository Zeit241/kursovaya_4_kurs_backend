package pin122.kursovaya.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pin122.kursovaya.dto.AuthResponse;
import pin122.kursovaya.dto.CreateUserDto;
import pin122.kursovaya.dto.CurrentUserDto;
import pin122.kursovaya.dto.LoginRequest;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.utils.EncryptPassword;
import pin122.kursovaya.utils.JwtTokenProvider;

/**
 * Аутентификация по email/паролю, выдача JWT и сбор профиля {@link CurrentUserDto} для ответа клиенту.
 */
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final CurrentUserDtoFactory currentUserDtoFactory;

    /**
     * @param userRepository          репозиторий пользователей
     * @param jwtTokenProvider        генерация JWT
     * @param authenticationManager   менеджер аутентификации Spring Security
     * @param userDetailsService      загрузка {@link UserDetails} по логину
     * @param currentUserDtoFactory   сборка расширенного DTO текущего пользователя
     */
    public AuthService(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            CurrentUserDtoFactory currentUserDtoFactory
    ) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.currentUserDtoFactory = currentUserDtoFactory;
    }

    /**
     * Выполняет вход: проверка учётных данных, выпуск токена и формирование {@link AuthResponse}.
     *
     * @param loginRequest email и пароль из запроса
     * @return ответ с JWT, кодом роли и {@link CurrentUserDto}
     * @throws org.springframework.security.core.AuthenticationException при неверных учётных данных
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getEmail());
        String token = jwtTokenProvider.generateToken(userDetails);

        User user = userRepository.findByEmailWithPatientAndDoctor(loginRequest.getEmail());
        String roleCode = null;
        if (user != null && user.getRole() != null) {
            roleCode = user.getRole().getCode();
        }
        CurrentUserDto currentUser = currentUserDtoFactory.build(user);

        return new AuthResponse(
                token,
                loginRequest.getEmail(),
                "Успешный вход в систему",
                roleCode,
                currentUser
        );
    }

    /**
     * Упрощённая проверка email/пароля без участия {@link AuthenticationManager} (например, для legacy-сценариев).
     *
     * @param email    email пользователя
     * @param password открытый пароль
     * @return {@link User}, если найден и пароль совпал с хешем; иначе {@code null}
     */
    public User authenticate(String email, String password) {
        User user = userRepository.findByEmail(email);
        if (user != null && EncryptPassword.verify(password, user.getPasswordHash())) {
            return user;
        }
        return null;

    }
}
