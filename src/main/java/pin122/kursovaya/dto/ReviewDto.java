package pin122.kursovaya.dto;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * DTO отзыва о приёме для REST: оценка, текст, связи с приёмом, врачом и пациентом.
 */
@Data
public class ReviewDto {
    private Long id;
    private Long appointmentId;
    private Long doctorId;
    private Long patientId;
    private String patientName;
    private Short rating;
    private String reviewText;
    private OffsetDateTime createdAt;

    public ReviewDto() {
    }

    /**
     * @param id идентификатор отзыва
     * @param appointmentId идентификатор приёма
     * @param doctorId идентификатор врача
     * @param patientId идентификатор пациента
     * @param patientName отображаемое имя пациента
     * @param rating оценка
     * @param reviewText текст отзыва
     * @param createdAt время создания
     */
    public ReviewDto(Long id, Long appointmentId, Long doctorId, Long patientId, String patientName, Short rating, String reviewText, OffsetDateTime createdAt) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.patientName = patientName;
        this.rating = rating;
        this.reviewText = reviewText;
        this.createdAt = createdAt;
    }
}