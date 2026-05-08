package pin122.kursovaya.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * DTO расписания врача для REST: дата, интервал работы, длительность слота, связи с врачом и кабинетом.
 */
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

    /**
     * @param id идентификатор расписания
     * @param doctorId идентификатор врача
     * @param roomId идентификатор кабинета
     * @param dateAt дата
     * @param startTime время начала смены/интервала
     * @param endTime время окончания
     * @param slotDurationMinutes длительность одного слота в минутах
     * @param createdAt время создания
     * @param updatedAt время обновления
     */
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