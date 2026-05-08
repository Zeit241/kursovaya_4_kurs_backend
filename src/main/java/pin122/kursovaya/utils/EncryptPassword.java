package pin122.kursovaya.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Утилита хеширования и проверки паролей на основе {@link BCryptPasswordEncoder}.
 *
 * <p>Используется при регистрации и аутентификации пользователей.
 */
public class EncryptPassword {
    private static final PasswordEncoder encoder = new BCryptPasswordEncoder(12);

    /**
     * Вычисляет BCrypt-хеш открытого пароля.
     *
     * @param plain открытый пароль в открытом виде
     * @return строка хеша для сохранения в БД
     */
    public static String hashPassword(String plain) {
        return encoder.encode(plain);
    }

    /**
     * Проверяет, соответствует ли открытый пароль сохранённому хешу.
     *
     * @param plain   открытый пароль
     * @param encoded ранее сохранённый BCrypt-хеш
     * @return {@code true}, если пароль совпадает с хешем
     */
    public static boolean verify(String plain, String encoded) {
        return encoder.matches(plain, encoded);
    }
}
