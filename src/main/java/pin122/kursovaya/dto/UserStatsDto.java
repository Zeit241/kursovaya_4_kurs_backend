package pin122.kursovaya.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO счётчиков активности пользователя для REST (приёмы, отзывы, записи в очереди).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsDto {
    private Long appointmentsCount;
    private Long reviewsCount;
    private Long queueEntriesCount;
}

