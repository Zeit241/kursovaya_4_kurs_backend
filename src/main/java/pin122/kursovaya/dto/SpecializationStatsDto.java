package pin122.kursovaya.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO статистики по специализации для дашборда (число врачей и справочные поля).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecializationStatsDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Long doctorCount;
}










