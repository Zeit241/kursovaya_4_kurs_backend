package pin122.kursovaya.utils;

import lombok.Data;

import java.util.List;

/**
 * Тело ответа API при ошибке валидации или обработки: список текстовых деталей для клиента.
 *
 * <p>Поля и аксессоры генерируются Lombok {@link Data}.
 */
@Data
public class ErrorResponse {

    /** Список сообщений об ошибках (например, по полям формы). */
    private List<String> details;
}
