package pin122.kursovaya.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pin122.kursovaya.dto.AuthResponse;
import pin122.kursovaya.dto.CreateUserDto;
import pin122.kursovaya.dto.LoginRequest;
import pin122.kursovaya.model.User;
import pin122.kursovaya.service.AuthService;
import pin122.kursovaya.service.UserService;
import pin122.kursovaya.utils.ApiResponse;
import pin122.kursovaya.utils.JwtTokenProvider;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты аутентификации и регистрации через сервисы и REST.
 */
@DisplayName("AuthFlowIT")
class AuthFlowIT extends AbstractIntegrationIT {

    private static final String PASSWORD = "AuthPass123!";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("login возвращает JWT для корректных учётных данных")
    void login_validCredentials_returnsJwt() {
        String email = uniqueEmail("login");
        createPatientUser(email, PASSWORD);

        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);

        AuthResponse response = authService.login(request);

        assertNotNull(response.getToken());
        assertFalse(response.getToken().isBlank());
        assertEquals(email, response.getEmail());
        assertEquals("patient", response.getRoleCode());
        assertTrue(jwtTokenProvider.validateToken(response.getToken()));
    }

    @Test
    @DisplayName("login выбрасывает исключение при неверном пароле")
    void login_wrongPassword_throwsAuthenticationException() {
        String email = uniqueEmail("bad-login");
        createPatientUser(email, PASSWORD);

        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword("WrongPassword!");

        assertThrows(Exception.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("register через UserService создаёт пользователя с ролью patient")
    void register_newUser_createsPatientProfile() {
        CreateUserDto dto = new CreateUserDto();
        dto.setEmail(uniqueEmail("register"));
        dto.setPhone(uniquePhone());
        dto.setPassword(PASSWORD);
        dto.setConfirmPassword(PASSWORD);
        dto.setFio("Петров Пётр Петрович");

        var created = userService.createUser(dto);

        assertTrue(created.isPresent());
        User saved = userRepository.findByEmail(dto.getEmail());
        assertNotNull(saved);
        assertNotNull(saved.getPatient());
        assertNotNull(saved.getRole());
        assertEquals("patient", saved.getRole().getCode());
    }

    @Test
    @DisplayName("register отклоняет дубликат email")
    void register_duplicateEmail_returnsEmpty() {
        String email = uniqueEmail("dup");
        String phone = uniquePhone();
        createPatientUser(email, PASSWORD);

        CreateUserDto dto = new CreateUserDto();
        dto.setEmail(email);
        dto.setPhone(phone);
        dto.setPassword(PASSWORD);
        dto.setConfirmPassword(PASSWORD);
        dto.setFio("Сидоров Сидор");

        assertTrue(userService.createUser(dto).isEmpty());
    }

    @Test
    @DisplayName("REST /api/auth/login возвращает 200 и обёртку ApiResponse")
    void restLogin_returnsOkWithApiResponse() {
        String email = uniqueEmail("rest-login");
        createPatientUser(email, PASSWORD);

        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"success\":true"));
    }
}
