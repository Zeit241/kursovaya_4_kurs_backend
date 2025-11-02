package pin122.kursovaya.dto;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class ReviewDto {
    private Long id;
    private Long appointmentId;
    private Long doctorId;
    private Long patientId;
    private Short rating;
    private String reviewText;
    private OffsetDateTime createdAt;

    // Constructors, Getters, Setters
    public ReviewDto() {
    }

    public ReviewDto(Long id, Long appointmentId, Long doctorId, Long patientId, Short rating, String reviewText, OffsetDateTime createdAt) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.rating = rating;
        this.reviewText = reviewText;
        this.createdAt = createdAt;
    }
}