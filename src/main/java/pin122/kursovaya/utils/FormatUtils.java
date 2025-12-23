package pin122.kursovaya.utils;

/**
 * Утилиты для форматирования данных
 */
public class FormatUtils {
    
    /**
     * Нормализует номер телефона к формату +79001110023
     * Убирает все пробелы, скобки, дефисы
     * 
     * @param phone Исходный номер телефона
     * @return Нормализованный номер или null, если входная строка null/пустая
     */
    public static String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        
        // Убираем все символы кроме цифр и +
        String normalized = phone.replaceAll("[^0-9+]", "");
        
        // Если номер начинается с 8, заменяем на +7
        if (normalized.startsWith("8") && normalized.length() == 11) {
            normalized = "+7" + normalized.substring(1);
        }
        // Если номер начинается с 7 и нет +, добавляем +
        else if (normalized.startsWith("7") && !normalized.startsWith("+7") && normalized.length() == 11) {
            normalized = "+" + normalized;
        }
        // Если номер не начинается с +, добавляем +7
        else if (!normalized.startsWith("+") && normalized.length() == 10) {
            normalized = "+7" + normalized;
        }
        
        return normalized;
    }
    
    /**
     * Нормализует СНИЛС к формату 123-456-789-04
     * Формат: XXX-XXX-XXX-XX
     * 
     * @param insuranceNumber Исходный СНИЛС
     * @return Нормализованный СНИЛС или null, если входная строка null/пустая
     */
    public static String normalizeInsuranceNumber(String insuranceNumber) {
        if (insuranceNumber == null || insuranceNumber.trim().isEmpty()) {
            return null;
        }
        
        // Убираем все символы кроме цифр
        String digits = insuranceNumber.replaceAll("[^0-9]", "");
        
        // Проверяем, что осталось 11 цифр
        if (digits.length() != 11) {
            return insuranceNumber.trim(); // Возвращаем исходное, если формат неверный
        }
        
        // Форматируем: XXX-XXX-XXX-XX
        return digits.substring(0, 3) + "-" + 
               digits.substring(3, 6) + "-" + 
               digits.substring(6, 9) + "-" + 
               digits.substring(9, 11);
    }
}




