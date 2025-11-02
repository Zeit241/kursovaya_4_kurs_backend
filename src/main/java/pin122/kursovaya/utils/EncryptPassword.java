package pin122.kursovaya.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class EncryptPassword {
    private static final PasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public static String hashPassword(String plain) {
        return encoder.encode(plain);
    }

    public static boolean verify(String plain, String encoded) {
        return encoder.matches(plain, encoded);
    }
}
