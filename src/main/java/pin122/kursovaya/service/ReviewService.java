package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.ReviewDto;
import pin122.kursovaya.model.Review;
import pin122.kursovaya.repository.ReviewRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Управление отзывами пациентов о врачах: выборка, создание, частичное обновление и удаление.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    /**
     * @param reviewRepository репозиторий отзывов
     */
    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    /**
     * Возвращает все отзывы, оставленные указанному врачу.
     *
     * @param doctorId идентификатор врача
     * @return список {@link ReviewDto}
     */
    public List<ReviewDto> getReviewsByDoctor(Long doctorId) {
        return reviewRepository.findByDoctorId(doctorId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Ищет отзыв по идентификатору.
     *
     * @param id первичный ключ отзыва
     * @return {@link ReviewDto}, если найден
     */
    public Optional<ReviewDto> getReviewById(Long id) {
        return reviewRepository.findById(id)
                .map(this::mapToDto);
    }

    /**
     * Ищет отзыв, привязанный к приёму (записи на приём).
     *
     * @param appointmentId идентификатор приёма
     * @return {@link ReviewDto}, если отзыв существует
     */
    public Optional<ReviewDto> getReviewByAppointmentId(Long appointmentId) {
        return reviewRepository.findByAppointmentId(appointmentId)
                .map(this::mapToDto);
    }

    /**
     * Сохраняет новый или изменённый отзыв.
     *
     * @param review сущность отзыва
     * @return DTO сохранённой записи
     */
    public ReviewDto saveReview(Review review) {
        Review saved = reviewRepository.save(review);
        return mapToDto(saved);
    }

    /**
     * Частично обновляет оценку и текст отзыва; поле {@code createdAt} не меняется.
     *
     * @param id            идентификатор отзыва
     * @param reviewUpdate  сущность с заполненными только изменяемыми полями
     * @return обновлённый {@link ReviewDto}, если отзыв найден
     */
    public Optional<ReviewDto> updateReview(Long id, Review reviewUpdate) {
        return reviewRepository.findById(id).map(existingReview -> {
            if (reviewUpdate.getRating() != null) {
                existingReview.setRating(reviewUpdate.getRating());
            }
            if (reviewUpdate.getReviewText() != null) {
                existingReview.setReviewText(reviewUpdate.getReviewText());
            }

            Review saved = reviewRepository.save(existingReview);
            return mapToDto(saved);
        });
    }

    /**
     * Удаляет отзыв по идентификатору.
     *
     * @param id первичный ключ отзыва
     */
    public void deleteReview(Long id) {
        reviewRepository.deleteById(id);
    }

    /**
     * Собирает {@link ReviewDto} с кратким ФИО пациента для отображения.
     *
     * @param review сущность из БД
     * @return DTO для API
     */
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

    /**
     * Склеивает непустые части ФИО в одну строку с пробелами.
     *
     * @param lastName  фамилия
     * @param firstName имя
     * @param middleName отчество
     * @return полное имя или {@code null}, если все части пусты
     */
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
