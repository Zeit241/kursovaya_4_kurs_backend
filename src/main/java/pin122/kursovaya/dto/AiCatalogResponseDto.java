package pin122.kursovaya.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Ответ REST API / внутренний перенос данных для каталога, подготовленного для ИИ:
 * краткие списки врачей и услуг.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiCatalogResponseDto {
    private List<AiDoctorRefDto> doctors = new ArrayList<>();
    private List<AiServiceRefDto> services = new ArrayList<>();
}
