package pin122.kursovaya.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Динамика посещаемости за один день.
 */
@Data
public class DailyAttendanceItemDto {
    private LocalDate date;
    private int totalCount;
    private int scheduledCount;
    private int inProgressCount;
    private int completedCount;
    private int cancelledCount;
}
