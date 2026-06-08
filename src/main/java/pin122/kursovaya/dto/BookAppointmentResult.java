package pin122.kursovaya.dto;

import lombok.Getter;

import java.util.Optional;

/**
 * Результат бронирования слота: либо DTO приёма, либо код ошибки для UI.
 */
@Getter
public class BookAppointmentResult {
    private final AppointmentDto appointment;
    private final String errorMessage;

    private BookAppointmentResult(AppointmentDto appointment, String errorMessage) {
        this.appointment = appointment;
        this.errorMessage = errorMessage;
    }

    public static BookAppointmentResult success(AppointmentDto dto) {
        return new BookAppointmentResult(dto, null);
    }

    public static BookAppointmentResult failure(String errorMessage) {
        return new BookAppointmentResult(null, errorMessage);
    }

    public boolean isSuccess() {
        return appointment != null;
    }

    public Optional<AppointmentDto> toOptional() {
        return isSuccess() ? Optional.of(appointment) : Optional.empty();
    }
}
