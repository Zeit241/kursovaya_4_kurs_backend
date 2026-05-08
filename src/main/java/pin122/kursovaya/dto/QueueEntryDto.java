package pin122.kursovaya.dto;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * DTO элемента очереди к врачу для REST: позиция, пациент, опциональная связь с приёмом.
 */
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

    /**
     * @param id идентификатор записи очереди
     * @param doctorId идентификатор врача
     * @param appointmentId идентификатор приёма или {@code null}
     * @param patientId идентификатор пациента
     * @param position порядковый номер в очереди
     * @param lastUpdated время последнего изменения
     */
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