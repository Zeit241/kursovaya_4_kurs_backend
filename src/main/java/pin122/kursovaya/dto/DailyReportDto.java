package pin122.kursovaya.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO сводного отчёта за календарный день: агрегированные счётчики по статусам приёмов и список детальных записей.
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

    /**
     * @param date дата отчёта
     * @param totalAppointments всего приёмов за день
     * @param scheduledCount число запланированных
     * @param completedCount число завершённых
     * @param cancelledCount число отменённых
     * @param noShowCount число неявок
     * @param doctorId идентификатор врача, если отчёт по одному врачу; иначе {@code null}
     * @param doctorDisplayName отображаемое имя врача для заголовка отчёта
     * @param appointments список приёмов с детализацией
     */
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









