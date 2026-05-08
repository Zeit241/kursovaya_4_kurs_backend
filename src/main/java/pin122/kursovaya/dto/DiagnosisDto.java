package pin122.kursovaya.dto;

import lombok.Data;
import pin122.kursovaya.model.Diagnosis;

/**
 * DTO справочника диагнозов для REST (код, название, категория).
 */
@Data
public class DiagnosisDto {
    private Long id;
    private String code;
    private String name;
    private String category;

    public DiagnosisDto() {
    }

    /**
     * @param diagnosis сущность диагноза из БД
     */
    public DiagnosisDto(Diagnosis diagnosis) {
        this.id = diagnosis.getId();
        this.code = diagnosis.getCode();
        this.name = diagnosis.getName();
        this.category = diagnosis.getCategory();
    }
}
