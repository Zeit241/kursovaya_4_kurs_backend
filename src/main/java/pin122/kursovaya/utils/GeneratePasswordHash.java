package pin122.kursovaya.utils;

/**
 * Утилита для генерации BCrypt хешей паролей
 * Запустить main метод для получения хеша пароля "password123"
 */
public class GeneratePasswordHash {
    public static void main(String[] args) {
        String password = "password123";
        String hash = EncryptPassword.hashPassword(password);
        System.out.println("Пароль: " + password);
        System.out.println("BCrypt хеш: " + hash);
    }
}

