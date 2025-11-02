package pin122.kursovaya.dto;

import lombok.Data;

import java.time.LocalTime;
import java.time.OffsetDateTime;

@Data
public class ScheduleDto {
    private Long id;
    private Long doctorId;
    private Integer roomId;
    private Short weekday; // 0 = Sunday, 1 = Monday, ..., 6 = Saturday
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer slotDurationMinutes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public ScheduleDto() {
    }

    public ScheduleDto(Long id, Long doctorId, Integer roomId, Short weekday,
                       LocalTime startTime, LocalTime endTime, Integer slotDurationMinutes,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.doctorId = doctorId;
        this.roomId = roomId;
        this.weekday = weekday;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotDurationMinutes = slotDurationMinutes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}