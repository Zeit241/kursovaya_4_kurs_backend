package pin122.kursovaya.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для JwtTokenProvider - провайдера JWT токенов
 */
@DisplayName("JwtTokenProvider - тесты JWT токенов")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        // Устанавливаем секретный ключ через reflection (минимум 256 бит = 32 символа)
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", 
            "mySecretKeyForJWTTokenGenerationAndValidationMustBeAtLeast256BitsLong");
        // 1 час в миллисекундах
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", 3600000L);
    }

    @Test
    @DisplayName("Генерация токена для UserDetails")
    void generateToken_withUserDetails_createsValidToken() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  ТЕСТ: Генерация JWT токена                                  ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        
        String username = "test@example.com";
        System.out.println("║  Создание токена для пользователя: " + username);
        
        UserDetails userDetails = User.builder()
                .username(username)
                .password("password")
                .authorities(Collections.emptyList())
                .build();

        String token = jwtTokenProvider.generateToken(userDetails);
        
        System.out.println("║  Сгенерированный токен: " + token.substring(0, 50) + "...");
        System.out.println("║  Длина токена: " + token.length() + " символов");

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains(".")); // JWT имеет формат xxx.yyy.zzz
        
        System.out.println("║  ✅ ТЕСТ ПРОЙДЕН УСПЕШНО!");
        System.out.println("║  JWT токен успешно сгенерирован и имеет корректный формат");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    @Test
    @DisplayName("Генерация токена по имени пользователя")
    void generateToken_withUsername_createsValidToken() {
        String token = jwtTokenProvider.generateToken("user@test.com");

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("Извлечение имени пользователя из токена")
    void extractUsername_fromValidToken_returnsCorrectUsername() {
        String expectedUsername = "doctor@clinic.com";
        String token = jwtTokenProvider.generateToken(expectedUsername);

        String extractedUsername = jwtTokenProvider.extractUsername(token);

        assertEquals(expectedUsername, extractedUsername);
    }

    @Test
    @DisplayName("Извлечение даты истечения токена")
    void extractExpiration_fromValidToken_returnsFutureDate() {
        String token = jwtTokenProvider.generateToken("user@test.com");

        Date expiration = jwtTokenProvider.extractExpiration(token);

        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()), "Дата истечения должна быть в будущем");
    }

    @Test
    @DisplayName("Валидация токена - валидный токен")
    void validateToken_validToken_returnsTrue() {
        String token = jwtTokenProvider.generateToken("user@test.com");

        Boolean isValid = jwtTokenProvider.validateToken(token);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("Валидация токена с UserDetails - совпадение имени")
    void validateToken_withMatchingUserDetails_returnsTrue() {
        String username = "patient@clinic.com";
        UserDetails userDetails = User.builder()
                .username(username)
                .password("password")
                .authorities(Collections.emptyList())
                .build();
        String token = jwtTokenProvider.generateToken(userDetails);

        Boolean isValid = jwtTokenProvider.validateToken(token, userDetails);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("Валидация токена - некорректный токен")
    void validateToken_invalidToken_returnsFalse() {
        Boolean isValid = jwtTokenProvider.validateToken("invalid.token.here");

        assertFalse(isValid);
    }

    @Test
    @DisplayName("Валидация токена с UserDetails - несовпадение имени")
    void validateToken_withDifferentUserDetails_returnsFalse() {
        String token = jwtTokenProvider.generateToken("user1@test.com");
        UserDetails differentUser = User.builder()
                .username("user2@test.com")
                .password("password")
                .authorities(Collections.emptyList())
                .build();

        Boolean isValid = jwtTokenProvider.validateToken(token, differentUser);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("Токен содержит три части разделённые точками")
    void generateToken_hasThreeParts() {
        String token = jwtTokenProvider.generateToken("test@test.com");
        
        String[] parts = token.split("\\.");
        
        assertEquals(3, parts.length, "JWT токен должен состоять из 3 частей");
    }

    @Test
    @DisplayName("Истёкший токен не проходит валидацию")
    void validateToken_expiredToken_returnsFalse() {
        // Устанавливаем время истечения в прошлое (-1 час)
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", -3600000L);
        
        String expiredToken = jwtTokenProvider.generateToken("expired@test.com");
        
        Boolean isValid = jwtTokenProvider.validateToken(expiredToken);
        
        assertFalse(isValid, "Истёкший токен не должен быть валидным");
    }
}
