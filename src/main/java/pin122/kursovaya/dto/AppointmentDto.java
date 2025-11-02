package pin122.kursovaya.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AppointmentDto {
    private Long id;
    private Long doctorId;
    private Long patientId;
    private Integer roomId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String status;
    private String source;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String cancelReason;

    public AppointmentDto() {}

    public AppointmentDto(Long id, Long doctorId, Long patientId, Integer roomId, OffsetDateTime startTime, OffsetDateTime endTime, String status, String source, Long createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt, String cancelReason) {
        this.id = id;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.roomId = roomId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.source = source;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.cancelReason = cancelReason;
    }
}
