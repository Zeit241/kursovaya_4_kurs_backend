package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import pin122.kursovaya.dto.AuthResponse;
import pin122.kursovaya.dto.CurrentUserDto;
import pin122.kursovaya.dto.LoginRequest;
import pin122.kursovaya.model.Role;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.utils.EncryptPassword;
import pin122.kursovaya.utils.JwtTokenProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для AuthService - сервис аутентификации
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - тесты сервиса аутентификации")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private CurrentUserDtoFactory currentUserDtoFactory;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private LoginRequest loginRequest;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setId(1L);
        role.setCode("patient");
        role.setName("Пациент");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("patient@test.com");
        testUser.setPasswordHash(EncryptPassword.hashPassword("password123"));
        testUser.setFirstName("Иван");
        testUser.setLastName("Петров");
        testUser.setRole(role);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("patient@test.com");
        loginRequest.setPassword("password123");

        userDetails = org.springframework.security.core.userdetails.User
                .withUsername("patient@test.com")
                .password("encoded")
                .roles("PATIENT")
                .build();
    }

    @Test
    @DisplayName("Вход - успешная аутентификация")
    void login_validCredentials_returnsAuthResponse() {
        CurrentUserDto currentUserDto = new CurrentUserDto(testUser);

        when(userDetailsService.loadUserByUsername("patient@test.com")).thenReturn(userDetails);
        when(jwtTokenProvider.generateToken(userDetails)).thenReturn("jwt-token-123");
        when(userRepository.findByEmailWithPatientAndDoctor("patient@test.com")).thenReturn(testUser);
        when(currentUserDtoFactory.build(testUser)).thenReturn(currentUserDto);

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("jwt-token-123", response.getToken());
        assertEquals("patient@test.com", response.getEmail());
        assertEquals("Успешный вход в систему", response.getMessage());
        assertEquals("patient", response.getRoleCode());
        assertNotNull(response.getUser());
    }

    @Test
    @DisplayName("Вход - вызывает AuthenticationManager")
    void login_validCredentials_callsAuthenticationManager() {
        when(userDetailsService.loadUserByUsername(any())).thenReturn(userDetails);
        when(jwtTokenProvider.generateToken(any(UserDetails.class))).thenReturn("token");
        when(userRepository.findByEmailWithPatientAndDoctor(any())).thenReturn(testUser);
        when(currentUserDtoFactory.build(any())).thenReturn(new CurrentUserDto(testUser));

        authService.login(loginRequest);

        verify(authenticationManager, times(1)).authenticate(any());
    }

    @Test
    @DisplayName("Вход - генерирует JWT токен")
    void login_validCredentials_generatesToken() {
        when(userDetailsService.loadUserByUsername("patient@test.com")).thenReturn(userDetails);
        when(jwtTokenProvider.generateToken(userDetails)).thenReturn("generated-jwt");
        when(userRepository.findByEmailWithPatientAndDoctor("patient@test.com")).thenReturn(testUser);
        when(currentUserDtoFactory.build(testUser)).thenReturn(new CurrentUserDto(testUser));

        AuthResponse response = authService.login(loginRequest);

        assertEquals("generated-jwt", response.getToken());
        verify(jwtTokenProvider, times(1)).generateToken(userDetails);
    }

    @Test
    @DisplayName("Вход - возвращает CurrentUserDto")
    void login_validCredentials_returnsCurrentUser() {
        CurrentUserDto currentUserDto = new CurrentUserDto(testUser);
        currentUserDto.setEmail("patient@test.com");

        when(userDetailsService.loadUserByUsername(any())).thenReturn(userDetails);
        when(jwtTokenProvider.generateToken(any(UserDetails.class))).thenReturn("token");
        when(userRepository.findByEmailWithPatientAndDoctor(any())).thenReturn(testUser);
        when(currentUserDtoFactory.build(testUser)).thenReturn(currentUserDto);

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response.getUser());
        assertEquals("patient@test.com", response.getUser().getEmail());
    }

    @Test
    @DisplayName("Вход - пользователь без роли, roleCode = null")
    void login_userWithoutRole_returnsNullRoleCode() {
        testUser.setRole(null);

        when(userDetailsService.loadUserByUsername(any())).thenReturn(userDetails);
        when(jwtTokenProvider.generateToken(any(UserDetails.class))).thenReturn("token");
        when(userRepository.findByEmailWithPatientAndDoctor(any())).thenReturn(testUser);
        when(currentUserDtoFactory.build(testUser)).thenReturn(new CurrentUserDto(testUser));

        AuthResponse response = authService.login(loginRequest);

        assertNull(response.getRoleCode());
    }

    @Test
    @DisplayName("Вход - неверные учётные данные, исключение")
    void login_invalidCredentials_throwsException() {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));
        verify(jwtTokenProvider, never()).generateToken(any(UserDetails.class));
    }

    @Test
    @DisplayName("authenticate - успешная проверка пароля")
    void authenticate_validPassword_returnsUser() {
        when(userRepository.findByEmail("patient@test.com")).thenReturn(testUser);

        User result = authService.authenticate("patient@test.com", "password123");

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("patient@test.com", result.getEmail());
    }

    @Test
    @DisplayName("authenticate - пользователь не найден")
    void authenticate_userNotFound_returnsNull() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(null);

        User result = authService.authenticate("unknown@test.com", "password123");

        assertNull(result);
    }

    @Test
    @DisplayName("authenticate - неверный пароль")
    void authenticate_wrongPassword_returnsNull() {
        when(userRepository.findByEmail("patient@test.com")).thenReturn(testUser);

        User result = authService.authenticate("patient@test.com", "wrong-password");

        assertNull(result);
    }

    @Test
    @DisplayName("authenticate - пользователь найден, но хеш пустой")
    void authenticate_nullPasswordHash_returnsNull() {
        testUser.setPasswordHash(null);
        when(userRepository.findByEmail("patient@test.com")).thenReturn(testUser);

        User result = authService.authenticate("patient@test.com", "password123");

        assertNull(result);
    }
}
