package pin122.kursovaya.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AppointmentDto {
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
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String cancelReason;
    private String diagnosis;

    public AppointmentDto() {}

    public AppointmentDto(Long id, Long scheduleId, Long doctorId, Long patientId, Long roomId, 
                         OffsetDateTime startTime, OffsetDateTime endTime, Boolean isBooked, String status, String source, 
                         Long createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt, String cancelReason, String diagnosis) {
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
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.cancelReason = cancelReason;
        this.diagnosis = diagnosis;
    }
}
