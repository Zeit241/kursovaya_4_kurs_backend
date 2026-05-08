package pin122.kursovaya.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * Тело REST-запроса на создание пациента вместе с учётной записью пользователя и медицинскими атрибутами профиля.
 */
@Data
public class CreatePatientRequest {
    
    @NotNull(message = "Данные пользователя обязательны")
    @Valid
    private UserDto user;
    
    private LocalDate birthDate;
    
    // 1 = male, 2 = female
    private Short gender;
    
    private String insuranceNumber;
}

