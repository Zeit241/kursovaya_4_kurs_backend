package pin122.kursovaya.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Финансовая сводка за один календарный день (завершённые приёмы).
 */
@Data
public class DailyFinancialItemDto {
    private LocalDate date;
    private BigDecimal revenue;
    private int completedCount;
}
