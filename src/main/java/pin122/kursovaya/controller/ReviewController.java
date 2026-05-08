package pin122.kursovaya.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.ReviewDto;
import pin122.kursovaya.model.Review;
import pin122.kursovaya.service.ReviewService;

import java.util.List;

/**
 * REST-контроллер отзывов о врачах и привязке к записям на приём.
 * <p>
 * Базовый путь: {@code /api/reviews}.
 *
 * @see ReviewService
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * @param reviewService сервис отзывов
     */
    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Возвращает все отзывы по врачу.
     *
     * @param doctorId идентификатор врача
     * @return HTTP 200 и список {@link ReviewDto}
     */
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<ReviewDto>> getByDoctor(@PathVariable Long doctorId) {
        return ResponseEntity.ok(reviewService.getReviewsByDoctor(doctorId));
    }

    /**
     * Возвращает отзыв по идентификатору.
     *
     * @param id первичный ключ отзыва
     * @return HTTP 200 и DTO или HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReviewDto> getById(@PathVariable Long id) {
        return reviewService.getReviewById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Возвращает отзыв, связанный с записью на приём.
     *
     * @param appointmentId идентификатор записи
     * @return HTTP 200 и DTO или HTTP 404
     */
    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<ReviewDto> getByAppointmentId(@PathVariable Long appointmentId) {
        return reviewService.getReviewByAppointmentId(appointmentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Создаёт отзыв.
     *
     * @param review тело сущности {@link Review}
     * @return HTTP 200 и сохранённый отзыв в виде DTO
     */
    @PostMapping
    public ResponseEntity<ReviewDto> create(@RequestBody Review review) {
        return ResponseEntity.ok(reviewService.saveReview(review));
    }

    /**
     * Обновляет отзыв по идентификатору.
     *
     * @param id     идентификатор отзыва
     * @param review новые данные
     * @return HTTP 200 и DTO или HTTP 404
     */
    @PutMapping("/{id}")
    public ResponseEntity<ReviewDto> update(@PathVariable Long id, @RequestBody Review review) {
        return reviewService.updateReview(id, review)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Удаляет отзыв по идентификатору.
     *
     * @param id идентификатор отзыва
     * @return HTTP 204 при успехе
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }
}