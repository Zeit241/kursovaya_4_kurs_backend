package pin122.kursovaya.utils;

import pin122.kursovaya.model.User;

/**
 * Вспомогательные функции нормализации строк (телефон, СНИЛС) и отображаемого имени врача по {@link User}.
 */
public class FormatUtils {

    /**
     * Формирует отображаемое ФИО врача из полей связанного {@link User}.
     *
     * <p>Колонка {@code display_name} в сущности врача в этом сценарии не используется.
     *
     * @param user пользователь врача (может быть {@code null})
     * @return строка ФИО через пробел или пустая строка, если {@code user == null}
     */
    public static String doctorDisplayName(User user) {
        if (user == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendPart(sb, user.getLastName());
        appendPart(sb, user.getFirstName());
        appendPart(sb, user.getMiddleName());
        return sb.toString();
    }

    /**
     * Добавляет непустой фрагмент к строке с разделителем-пробелом.
     *
     * @param sb   накапливающий {@link StringBuilder}
     * @param part фрагмент ФИО (может быть {@code null} или пустым)
     */
    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(part.trim());
    }

    /**
     * Приводит номер телефона к виду {@code +79001110023}: удаляет пробелы, скобки и дефисы, нормализует префикс для РФ.
     *
     * @param phone исходный номер
     * @return нормализованный номер или {@code null}, если вход {@code null} или пустой после trim
     */
    public static String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }

        String normalized = phone.replaceAll("[^0-9+]", "");

        if (normalized.startsWith("8") && normalized.length() == 11) {
            normalized = "+7" + normalized.substring(1);
        } else if (normalized.startsWith("7") && !normalized.startsWith("+7") && normalized.length() == 11) {
            normalized = "+" + normalized;
        } else if (!normalized.startsWith("+") && normalized.length() == 10) {
            normalized = "+7" + normalized;
        }

        return normalized;
    }

    /**
     * Приводит номер полиса/СНИЛС к виду {@code XXX-XXX-XXX-XX}; при неверной длине возвращает trim исходной строки.
     *
     * @param insuranceNumber исходная строка
     * @return строка из 11 цифр с дефисами или исходный trim, если после удаления нецифровых символов длина не 11
     */
    public static String normalizeInsuranceNumber(String insuranceNumber) {
        if (insuranceNumber == null || insuranceNumber.trim().isEmpty()) {
            return null;
        }

        String digits = insuranceNumber.replaceAll("[^0-9]", "");

        if (digits.length() != 11) {
            return insuranceNumber.trim();
        }

        return digits.substring(0, 3) + "-"
                + digits.substring(3, 6) + "-"
                + digits.substring(6, 9) + "-"
                + digits.substring(9, 11);
    }
}
