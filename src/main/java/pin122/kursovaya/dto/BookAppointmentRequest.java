package pin122.kursovaya.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Тело REST-запроса на бронирование слота приёма: идентификаторы записи, пользователя и опционально услуги.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookAppointmentRequest {
    @NotNull
    private Long appointmentId;
    @NotNull
    private Long userId;
    /** Опционально: если у слота нет услуги — проставить; если есть — должен совпадать */
    private Long serviceId;
}
