package pin122.kursovaya.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ответ REST API после регистрации с созданием профиля пациента: JWT и данные пациента.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterWithPatientResponse {
    private String token;
    private PatientDto patient;
    private String message;
}

