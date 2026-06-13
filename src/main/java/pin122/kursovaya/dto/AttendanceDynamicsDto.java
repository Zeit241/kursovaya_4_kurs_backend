package pin122.kursovaya.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Динамика посещаемости за период по дням.
 */
@Data
public class AttendanceDynamicsDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<DailyAttendanceItemDto> dailyItems;
}
