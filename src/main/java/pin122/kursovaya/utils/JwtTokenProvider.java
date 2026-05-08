package pin122.kursovaya.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Генерация и разбор JWT, проверка срока действия и соответствия пользователю.
 *
 * <p>Секрет и время жизни задаются свойствами {@code jwt.secret} и {@code jwt.expiration}.
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:mySecretKeyForJWTTokenGenerationAndValidationMustBeAtLeast256BitsLong}")
    private String secret;

    /** Срок жизни токена в миллисекундах (по умолчанию 24 часа). */
    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    /**
     * Строит ключ подписи HMAC-SHA из секрета конфигурации.
     *
     * @return секретный ключ для подписи и проверки JWT
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Извлекает из токена subject — как правило, email (логин) пользователя.
     *
     * @param token строка JWT
     * @return значение subject из claims
     * @throws io.jsonwebtoken.JwtException при невалидной подписи, истечении срока или повреждённом токене
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Возвращает момент истечения срока действия токена.
     *
     * @param token строка JWT
     * @return дата истечения
     * @throws io.jsonwebtoken.JwtException при ошибке разбора токена
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Извлекает из токена произвольное поле claims с помощью переданной функции.
     *
     * @param token          строка JWT
     * @param claimsResolver функция, выбирающая нужное значение из {@link Claims}
     * @param <T>            тип извлекаемого значения
     * @return результат применения {@code claimsResolver}
     * @throws io.jsonwebtoken.JwtException при ошибке разбора токена
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Парсит JWT и возвращает полный набор claims после проверки подписи.
     *
     * @param token строка JWT
     * @return распарсенные claims
     * @throws io.jsonwebtoken.JwtException при невалидной подписи или повреждённом токене
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Проверяет, истёк ли срок действия токена относительно текущего времени.
     *
     * @param token строка JWT
     * @return {@code true}, если дата истечения раньше «сейчас»
     */
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Создаёт JWT для пользователя Spring Security; subject — {@link UserDetails#getUsername()}.
     *
     * @param userDetails данные пользователя из контекста аутентификации
     * @return компактное представление JWT
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    /**
     * Создаёт JWT с указанным subject (обычно email) и пустым набором дополнительных claims.
     *
     * @param username значение subject в токене
     * @return компактное представление JWT
     */
    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, username);
    }

    /**
     * Собирает подписанный JWT с заданными claims и subject.
     *
     * @param claims  дополнительные claims (может быть пустым)
     * @param subject идентификатор пользователя в поле subject
     * @return компактная строка JWT
     */
    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Проверяет, что subject токена совпадает с пользователем и срок действия не истёк.
     *
     * @param token       строка JWT
     * @param userDetails ожидаемый пользователь
     * @return {@code true}, если токен валиден для данного пользователя
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    /**
     * Проверяет только срок действия и целостность токена (без сравнения с {@link UserDetails}).
     *
     * @param token строка JWT
     * @return {@code false} при любой ошибке разбора или истечении срока; иначе {@code true}
     */
    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
