package pin122.kursovaya.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CreateScheduleRequest {
    private DoctorIdDto doctor;
    private RoomIdDto room;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateAt;
    
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;
    
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;
    
    private Integer slotDurationMinutes;

    @Data
    public static class DoctorIdDto {
        private Long id;
    }

    @Data
    public static class RoomIdDto {
        private Long id;
    }
}

