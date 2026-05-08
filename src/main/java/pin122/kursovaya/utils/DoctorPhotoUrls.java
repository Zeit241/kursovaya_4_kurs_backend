package pin122.kursovaya.utils;

import java.util.regex.Pattern;

/**
 * Хранение фото врача: в БД — UUID файла Directus или полный URL; отдача клиентам — публичный URL ассета.
 */
public final class DoctorPhotoUrls {

    private static final Pattern DIRECTUS_FILE_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private DoctorPhotoUrls() {
    }

    /**
     * База без Spring (например в DTO до инжекта): переменная окружения или localhost.
     */
    public static String resolveConfiguredPublicBase() {
        String fromEnv = System.getenv("APP_DIRECTUS_PUBLIC_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.replaceAll("/+$", "");
        }
        return "http://localhost:8055";
    }

    public static boolean isDirectusFileId(String value) {
        return value != null && DIRECTUS_FILE_UUID.matcher(value.trim()).matches();
    }

    public static boolean isAbsoluteHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        return v.startsWith("http://") || v.startsWith("https://");
    }

    /**
     * Нормализует значение для сохранения в БД: UUID (lower), полный URL или {@code null}.
     *
     * @param input сырой ввод из API (UUID, URL; base64 больше не поддерживается)
     * @return строка для колонки {@code doctors.photo} или {@code null}
     */
    public static String normalizeForStorage(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (isAbsoluteHttpUrl(s)) {
            return s;
        }
        if (isDirectusFileId(s)) {
            return s.toLowerCase();
        }
        // UUID без дефисов не принимаем
        return null;
    }

    /**
     * Строка для JSON: полный URL картинки для UI или {@code null}.
     *
     * @param stored   значение из БД
     * @param directusPublicBaseUrl базовый URL Directus без завершающего слэша, например http://localhost:8055
     */
    public static String toPublicImageUrl(String stored, String directusPublicBaseUrl) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String s = stored.trim();
        if (isAbsoluteHttpUrl(s)) {
            return s;
        }
        if (isDirectusFileId(s)) {
            String base = directusPublicBaseUrl == null ? "" : directusPublicBaseUrl.replaceAll("/+$", "");
            if (base.isEmpty()) {
                return null;
            }
            return base + "/assets/" + s + "?format=webp";
        }
        return null;
    }
}
