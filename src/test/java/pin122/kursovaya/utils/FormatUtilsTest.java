package pin122.kursovaya.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для класса FormatUtils - утилиты форматирования данных
 */
@DisplayName("FormatUtils - тесты утилит форматирования")
class FormatUtilsTest {

    // ========== Тесты normalizePhone ==========

    @Test
    @DisplayName("Нормализация телефона: 8XXXXXXXXXX -> +7XXXXXXXXXX")
    void normalizePhone_startsWithEight_replacesWithPlusSeven() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  ТЕСТ: Нормализация номера телефона                          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  Описание: Проверка преобразования номера телефона           ║");
        System.out.println("║  из формата 8XXXXXXXXXX в международный формат +7XXXXXXXXXX  ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        
        String inputPhone = "89001234567";
        String expectedResult = "+79001234567";
        
        System.out.println("║  Входные данные: " + inputPhone + "                              ║");
        System.out.println("║  Ожидаемый результат: " + expectedResult + "                         ║");
        
        String result = FormatUtils.normalizePhone(inputPhone);
        
        System.out.println("║  Полученный результат: " + result + "                        ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        
        assertEquals(expectedResult, result);
        
        System.out.println("║  ✅ ТЕСТ ПРОЙДЕН УСПЕШНО!                                    ║");
        System.out.println("║  Номер телефона корректно преобразован в формат +7           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    @Test
    @DisplayName("Нормализация телефона: 7XXXXXXXXXX -> +7XXXXXXXXXX")
    void normalizePhone_startsWithSeven_addsPlusSign() {
        String result = FormatUtils.normalizePhone("79001234567");
        assertEquals("+79001234567", result);
    }

    @Test
    @DisplayName("Нормализация телефона: удаление скобок и дефисов")
    void normalizePhone_withBracketsAndDashes_cleansFormat() {
        String result = FormatUtils.normalizePhone("+7 (900) 123-45-67");
        assertEquals("+79001234567", result);
    }

    @Test
    @DisplayName("Нормализация телефона: 10-значный номер -> добавление +7")
    void normalizePhone_tenDigits_addsPlusSeven() {
        String result = FormatUtils.normalizePhone("9001234567");
        assertEquals("+79001234567", result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Нормализация телефона: null или пустая строка -> null")
    void normalizePhone_nullOrEmpty_returnsNull(String input) {
        assertNull(FormatUtils.normalizePhone(input));
    }

    @Test
    @DisplayName("Нормализация телефона: только пробелы -> null")
    void normalizePhone_onlySpaces_returnsNull() {
        assertNull(FormatUtils.normalizePhone("   "));
    }

    // ========== Тесты normalizeInsuranceNumber (СНИЛС) ==========

    @Test
    @DisplayName("Нормализация СНИЛС: 11 цифр -> формат XXX-XXX-XXX-XX")
    void normalizeInsuranceNumber_elevenDigits_formatsCorrectly() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  ТЕСТ: Нормализация СНИЛС                                    ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        
        String input = "12345678904";
        String expected = "123-456-789-04";
        
        System.out.println("║  Входные данные: " + input);
        System.out.println("║  Ожидаемый результат: " + expected);
        
        String result = FormatUtils.normalizeInsuranceNumber(input);
        
        System.out.println("║  Полученный результат: " + result);
        
        assertEquals(expected, result);
        
        System.out.println("║  ✅ ТЕСТ ПРОЙДЕН УСПЕШНО!");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    @Test
    @DisplayName("Нормализация СНИЛС: с разделителями -> формат XXX-XXX-XXX-XX")
    void normalizeInsuranceNumber_withDelimiters_formatsCorrectly() {
        String result = FormatUtils.normalizeInsuranceNumber("123 456 789 04");
        assertEquals("123-456-789-04", result);
    }

    @Test
    @DisplayName("Нормализация СНИЛС: уже отформатированный -> без изменений")
    void normalizeInsuranceNumber_alreadyFormatted_keepsFormat() {
        String result = FormatUtils.normalizeInsuranceNumber("123-456-789-04");
        assertEquals("123-456-789-04", result);
    }

    @Test
    @DisplayName("Нормализация СНИЛС: неверное количество цифр -> исходная строка")
    void normalizeInsuranceNumber_wrongDigitCount_returnsOriginal() {
        String result = FormatUtils.normalizeInsuranceNumber("1234567890");
        assertEquals("1234567890", result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Нормализация СНИЛС: null или пустая строка -> null")
    void normalizeInsuranceNumber_nullOrEmpty_returnsNull(String input) {
        assertNull(FormatUtils.normalizeInsuranceNumber(input));
    }

    @ParameterizedTest
    @CsvSource({
        "'+7 (999) 123-45-67', '+79991234567'",
        "'8-800-555-35-35', '+78005553535'",
        "'+7(495)1234567', '+74951234567'"
    })
    @DisplayName("Нормализация телефона: различные форматы")
    void normalizePhone_variousFormats_normalizesCorrectly(String input, String expected) {
        assertEquals(expected, FormatUtils.normalizePhone(input));
    }
}
