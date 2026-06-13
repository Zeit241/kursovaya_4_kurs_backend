package pin122.kursovaya.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Персональная панель расписания врача: период, сводка по статусам и список приёмов.
 */
@Data
public class DoctorScheduleSummaryDto {
    private Long doctorId;
    private String doctorDisplayName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String viewMode;
    private int scheduledCount;
    private int inProgressCount;
    private int completedCount;
    private int cancelledCount;
    private int totalCount;
    private List<AppointmentDto> appointments;
}
