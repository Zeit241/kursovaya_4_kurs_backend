package pin122.kursovaya.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Тело REST-запроса на замену набора специализаций, привязанных к услуге.
 */
@Data
public class SetServiceSpecializationsRequest {
    private List<Long> specializationIds = new ArrayList<>();
}
