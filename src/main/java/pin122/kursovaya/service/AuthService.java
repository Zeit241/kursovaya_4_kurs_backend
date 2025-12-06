package pin122.kursovaya.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.AuthResponse;
import pin122.kursovaya.dto.CreateUserDto;
import pin122.kursovaya.dto.LoginRequest;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.utils.EncryptPassword;
import pin122.kursovaya.utils.JwtTokenProvider;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    public AuthService(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService
    ) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
    }

    public AuthResponse login(LoginRequest loginRequest) {
        // Аутентификация через Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        // Загружаем пользователя и генерируем токен
        UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getEmail());
        String token = jwtTokenProvider.generateToken(userDetails);

        // Получаем пользователя для извлечения роли
        User user = userRepository.findByEmail(loginRequest.getEmail());
        String roleCode = null;
        if (user != null && user.getRoles() != null && !user.getRoles().isEmpty()) {
            // Берем код первой роли
            roleCode = user.getRoles().iterator().next().getCode();
        }

        return new AuthResponse(token, loginRequest.getEmail(), "Успешный вход в систему", roleCode);
    }

    public User authenticate(String email, String password) {
        User user = userRepository.findByEmail(email);
        if (user != null && EncryptPassword.verify(password, user.getPasswordHash())) {
            return user;
        }
        return null;

    }
}