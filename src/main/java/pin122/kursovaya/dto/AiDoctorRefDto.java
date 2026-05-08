package pin122.kursovaya.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Краткая ссылка на врача в составе каталога для ИИ (идентификатор, имя, специализация).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDoctorRefDto {
    private Long id;
    private String name;
    private String specialization;
}
