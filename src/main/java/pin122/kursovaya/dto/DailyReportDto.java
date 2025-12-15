package pin122.kursovaya.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO для сводного отчёта за день
 */
@Data
public class DailyReportDto {
    private LocalDate date;
    
    // Статистика
    private int totalAppointments;
    private int scheduledCount;
    private int completedCount;
    private int cancelledCount;
    private int noShowCount;
    
    // Информация о враче (если отчёт по конкретному врачу)
    private Long doctorId;
    private String doctorDisplayName;
    
    // Список записей
    private List<ReportAppointmentDto> appointments;

    public DailyReportDto() {}

    public DailyReportDto(LocalDate date, int totalAppointments, int scheduledCount, 
                          int completedCount, int cancelledCount, int noShowCount,
                          Long doctorId, String doctorDisplayName, 
                          List<ReportAppointmentDto> appointments) {
        this.date = date;
        this.totalAppointments = totalAppointments;
        this.scheduledCount = scheduledCount;
        this.completedCount = completedCount;
        this.cancelledCount = cancelledCount;
        this.noShowCount = noShowCount;
        this.doctorId = doctorId;
        this.doctorDisplayName = doctorDisplayName;
        this.appointments = appointments;
    }
}



