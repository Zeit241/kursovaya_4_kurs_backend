package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.ReviewDto;
import pin122.kursovaya.model.Review;
import pin122.kursovaya.repository.ReviewRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public List<ReviewDto> getReviewsByDoctor(Long doctorId) {
        return reviewRepository.findByDoctorId(doctorId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public Optional<ReviewDto> getReviewById(Long id) {
        return reviewRepository.findById(id)
                .map(this::mapToDto);
    }

    public Optional<ReviewDto> getReviewByAppointmentId(Long appointmentId) {
        return reviewRepository.findByAppointmentId(appointmentId)
                .map(this::mapToDto);
    }

    public ReviewDto saveReview(Review review) {
        Review saved = reviewRepository.save(review);
        return mapToDto(saved);
    }

    public Optional<ReviewDto> updateReview(Long id, Review reviewUpdate) {
        return reviewRepository.findById(id).map(existingReview -> {
            // Обновляем только изменяемые поля: rating и reviewText
            if (reviewUpdate.getRating() != null) {
                existingReview.setRating(reviewUpdate.getRating());
            }
            if (reviewUpdate.getReviewText() != null) {
                existingReview.setReviewText(reviewUpdate.getReviewText());
            }
            // createdAt не обновляем, оно остается исходным
            
            Review saved = reviewRepository.save(existingReview);
            return mapToDto(saved);
        });
    }

    public void deleteReview(Long id) {
        reviewRepository.deleteById(id);
    }

    private ReviewDto mapToDto(Review review) {
        String patientName = null;
        if (review.getPatient() != null && review.getPatient().getUser() != null) {
            patientName = buildPatientName(
                    review.getPatient().getUser().getLastName(),
                    review.getPatient().getUser().getFirstName(),
                    review.getPatient().getUser().getMiddleName()
            );
        }
        
        return new ReviewDto(
                review.getId(),
                review.getAppointment() != null ? review.getAppointment().getId() : null,
                review.getDoctor() != null ? review.getDoctor().getId() : null,
                review.getPatient() != null ? review.getPatient().getId() : null,
                patientName,
                review.getRating(),
                review.getReviewText(),
                review.getCreatedAt()
        );
    }

    private String buildPatientName(String lastName, String firstName, String middleName) {
        if (lastName == null && firstName == null && middleName == null) {
            return null;
        }
        
        StringBuilder name = new StringBuilder();
        
        if (lastName != null && !lastName.trim().isEmpty()) {
            name.append(lastName.trim());
        }
        
        if (firstName != null && !firstName.trim().isEmpty()) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(firstName.trim());
        }
        
        if (middleName != null && !middleName.trim().isEmpty()) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(middleName.trim());
        }
        
        return name.length() > 0 ? name.toString() : null;
    }
}