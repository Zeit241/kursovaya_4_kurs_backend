package pin122.kursovaya.dto;

import lombok.Data;

/**
 * Загрузка одного врача за период: счётчики по ключевым статусам приёмов.
 */
@Data
public class DoctorWorkloadItemDto {
    private Long doctorId;
    private String doctorDisplayName;
    private int scheduledCount;
    private int inProgressCount;
    private int completedCount;
    private int cancelledCount;
    private int totalCount;
}
