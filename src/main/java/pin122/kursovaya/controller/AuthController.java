package pin122.kursovaya.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.AuthResponse;
import pin122.kursovaya.dto.CreateUserDto;
import pin122.kursovaya.dto.CreateUserWithPatientDto;
import pin122.kursovaya.dto.LoginRequest;
import pin122.kursovaya.dto.RegisterResponse;
import pin122.kursovaya.dto.RegisterWithPatientResponse;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.service.AuthService;
import pin122.kursovaya.service.UserService;
import pin122.kursovaya.utils.ApiResponse;
import pin122.kursovaya.utils.JwtTokenProvider;

/**
 * REST-контроллер аутентификации и регистрации.
 * <p>
 * Базовый путь: {@code /api/auth}. Эндпоинты входа и регистрации доступны без JWT; ответы обёрнуты в {@link ApiResponse}.
 *
 * @see AuthService
 * @see UserService
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * Создаёт контроллер аутентификации.
     *
     * @param authService          сервис входа и выдачи токенов
     * @param userService          сервис пользователей и регистрации
     * @param jwtTokenProvider     провайдер JWT после регистрации
     * @param userDetailsService   загрузка {@link UserDetails} по email для генерации токена
     */
    public AuthController(AuthService authService, UserService userService, 
                         JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.authService = authService;
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Выполняет вход по email и паролю и возвращает JWT в обёртке {@link ApiResponse}.
     *
     * @param loginRequest тело запроса с учётными данными
     * @param result       результат привязки и валидации полей
     * @return HTTP 200 с {@link AuthResponse} при успехе; HTTP 400 при ошибках валидации или неверных данных
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Ошибка валидации", result.getAllErrors()));
        }

        try {
            AuthResponse response = authService.login(loginRequest);
            return ResponseEntity.ok(new ApiResponse<>(true, "Успешный вход", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Неверный email или пароль", null));
        }
    }

    /**
     * Регистрирует пользователя без профиля пациента и возвращает JWT при успехе.
     *
     * @param registerRequest данные нового пользователя и подтверждение пароля
     * @param result          результат валидации
     * @return HTTP 200 с {@link RegisterResponse} при успехе; HTTP 400 при ошибках валидации, несовпадении паролей,
     *         занятом email/телефоне или сбое регистрации
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody CreateUserDto registerRequest, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Ошибка валидации", result.getAllErrors()));
        }

        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Пароли не совпадают", null));
        }

        try {
            var userDto = userService.createUser(registerRequest);
            if (userDto.isPresent()) {
                // Генерируем JWT токен для зарегистрированного пользователя
                UserDetails userDetails = userDetailsService.loadUserByUsername(registerRequest.getEmail());
                String token = jwtTokenProvider.generateToken(userDetails);
                
                RegisterResponse response = new RegisterResponse(
                    token,
                    userDto.get(),
                    "Пользователь успешно зарегистрирован"
                );
                return ResponseEntity.ok(new ApiResponse<>(true, "Пользователь успешно зарегистрирован", response));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Пользователь с таким email или телефоном уже существует", null));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Ошибка при регистрации: " + e.getMessage(), null));
        }
    }

    /**
     * Регистрирует пользователя вместе с карточкой пациента и возвращает JWT при успехе.
     *
     * @param registerRequest данные пользователя, пациента и подтверждение пароля
     * @param result          результат валидации
     * @return HTTP 200 с {@link RegisterWithPatientResponse} при успехе; HTTP 400 при ошибках валидации,
     *         несовпадении паролей, занятом email/телефоне или сбое регистрации
     */
    @PostMapping("/register-with-patient")
    public ResponseEntity<?> registerWithPatient(@Valid @RequestBody CreateUserWithPatientDto registerRequest, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Ошибка валидации", result.getAllErrors()));
        }

        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Пароли не совпадают", null));
        }

        try {
            var patientDto = userService.createUserWithPatient(registerRequest);
            if (patientDto.isPresent()) {
                // Генерируем JWT токен для зарегистрированного пользователя
                UserDetails userDetails = userDetailsService.loadUserByUsername(registerRequest.getEmail());
                String token = jwtTokenProvider.generateToken(userDetails);
                
                RegisterWithPatientResponse response = new RegisterWithPatientResponse(
                    token,
                    patientDto.get(),
                    "Пользователь и пациент успешно зарегистрированы"
                );
                return ResponseEntity.ok(new ApiResponse<>(true, "Пользователь и пациент успешно зарегистрированы", response));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Пользователь с таким email или телефоном уже существует", null));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Ошибка при регистрации: " + e.getMessage(), null));
        }
    }
}

