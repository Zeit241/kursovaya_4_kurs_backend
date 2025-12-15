package pin122.kursovaya.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDto {
    // Топ 5 специальностей по количеству врачей
    private List<SpecializationStatsDto> topSpecializations;
    
    // Топ 10 врачей по рейтингу
    private List<DoctorDto> topDoctors;
    
    // Запланированные записи текущего пользователя (с информацией о враче)
    private List<DashboardAppointmentDto> upcomingAppointments;
}




