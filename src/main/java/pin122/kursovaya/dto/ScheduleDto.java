package pin122.kursovaya.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Data
public class ScheduleDto {
    private Long id;
    private Long doctorId;
    private Long roomId;
    private LocalDate dateAt;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer slotDurationMinutes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public ScheduleDto() {
    }

    public ScheduleDto(Long id, Long doctorId, Long roomId, LocalDate dateAt,
                       LocalTime startTime, LocalTime endTime, Integer slotDurationMinutes,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.doctorId = doctorId;
        this.roomId = roomId;
        this.dateAt = dateAt;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotDurationMinutes = slotDurationMinutes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}