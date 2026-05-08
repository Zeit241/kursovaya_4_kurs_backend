package pin122.kursovaya.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Краткая ссылка на медицинскую услугу в составе каталога для ИИ.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiServiceRefDto {
    private Long id;
    private String name;
}
