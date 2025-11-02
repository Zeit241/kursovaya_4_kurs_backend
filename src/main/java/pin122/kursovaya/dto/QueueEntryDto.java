package pin122.kursovaya.dto;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class QueueEntryDto {
    private Long id;
    private Long doctorId;
    private Long appointmentId; // может быть null, если очередь без записи
    private Long patientId;
    private Integer position;
    private OffsetDateTime lastUpdated;

    public QueueEntryDto() {
    }

    public QueueEntryDto(Long id, Long doctorId, Long appointmentId, Long patientId,
                         Integer position, OffsetDateTime lastUpdated) {
        this.id = id;
        this.doctorId = doctorId;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.position = position;
        this.lastUpdated = lastUpdated;
    }
}