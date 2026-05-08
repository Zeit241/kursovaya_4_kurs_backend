package pin122.kursovaya.dto;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * DTO свободного или доступного для выбора слота приёма без вложенных сущностей (для каталога записи).
 */
@Data
public class AvailableAppointmentDto {
    private Long id;
    private Long scheduleId;
    private Long doctorId;
    private Long patientId;
    private Long roomId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Boolean isBooked;
    private String status;
    private String source;

    public AvailableAppointmentDto() {}

    /**
     * @param id идентификатор слота
     * @param scheduleId идентификатор расписания
     * @param doctorId идентификатор врача
     * @param patientId идентификатор пациента, если слот занят
     * @param roomId идентификатор кабинета
     * @param startTime начало
     * @param endTime конец
     * @param isBooked признак брони
     * @param status статус
     * @param source источник
     */
    public AvailableAppointmentDto(Long id, Long scheduleId, Long doctorId, Long patientId, Long roomId,
                                   OffsetDateTime startTime, OffsetDateTime endTime, Boolean isBooked,
                                   String status, String source) {
        this.id = id;
        this.scheduleId = scheduleId;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.roomId = roomId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isBooked = isBooked;
        this.status = status;
        this.source = source;
    }
}


