package pin122.kursovaya.utils;

/**
 * Вспомогательный класс для локальной генерации BCrypt-хеша пароля (запуск {@link #main(String[])}).
 *
 * <p>Не используется в рантайме приложения; удобен для подготовки тестовых данных и миграций.
 */
public class GeneratePasswordHash {

    /**
     * Печатает в консоль демонстрационный пароль и его BCrypt-хеш (через {@link EncryptPassword#hashPassword(String)}).
     *
     * @param args аргументы командной строки (не используются)
     */
    public static void main(String[] args) {
        String password = "password123";
        String hash = EncryptPassword.hashPassword(password);
        System.out.println("Пароль: " + password);
        System.out.println("BCrypt хеш: " + hash);
    }
}
