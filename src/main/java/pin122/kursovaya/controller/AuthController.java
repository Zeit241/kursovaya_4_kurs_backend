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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public AuthController(AuthService authService, UserService userService, 
                         JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.authService = authService;
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

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

