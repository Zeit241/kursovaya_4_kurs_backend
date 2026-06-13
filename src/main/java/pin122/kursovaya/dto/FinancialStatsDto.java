package pin122.kursovaya.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Финансовая статистика за период: выручка по завершённым приёмам.
 */
@Data
public class FinancialStatsDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalRevenue;
    private int completedCount;
    private BigDecimal averageCheck;
    private List<DailyFinancialItemDto> dailyBreakdown;
}
