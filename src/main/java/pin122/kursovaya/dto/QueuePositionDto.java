package pin122.kursovaya.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueuePositionDto {
    private Long queueEntryId;
    private Long doctorId;
    private Long patientId;
    private Integer position;
    private Boolean isNext;
    private String message;
}

