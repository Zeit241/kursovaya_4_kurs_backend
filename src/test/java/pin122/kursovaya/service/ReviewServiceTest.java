package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pin122.kursovaya.dto.ReviewDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.Review;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.ReviewRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для ReviewService - сервис отзывов пациентов
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService - тесты сервиса отзывов")
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private ReviewService reviewService;

    private Review testReview;
    private Doctor testDoctor;
    private Patient testPatient;

    @BeforeEach
    void setUp() {
        User doctorUser = new User();
        doctorUser.setId(1L);
        doctorUser.setFirstName("Андрей");
        doctorUser.setLastName("Докторов");

        testDoctor = new Doctor();
        testDoctor.setId(1L);
        testDoctor.setUser(doctorUser);

        User patientUser = new User();
        patientUser.setId(2L);
        patientUser.setFirstName("Иван");
        patientUser.setLastName("Петров");
        patientUser.setMiddleName("Сергеевич");

        testPatient = new Patient();
        testPatient.setId(5L);
        testPatient.setUser(patientUser);

        Appointment appointment = new Appointment();
        appointment.setId(100L);

        testReview = new Review();
        testReview.setId(1L);
        testReview.setDoctor(testDoctor);
        testReview.setPatient(testPatient);
        testReview.setAppointment(appointment);
        testReview.setRating((short) 5);
        testReview.setReviewText("Отличный врач!");
        testReview.setCreatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Получение отзывов по врачу")
    void getReviewsByDoctor_returnsList() {
        when(reviewRepository.findByDoctorId(1L)).thenReturn(List.of(testReview));

        List<ReviewDto> result = reviewService.getReviewsByDoctor(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals((short) 5, result.get(0).getRating());
        assertEquals("Петров Иван Сергеевич", result.get(0).getPatientName());
        verify(reviewRepository, times(1)).findByDoctorId(1L);
    }

    @Test
    @DisplayName("Получение отзыва по ID - найден")
    void getReviewById_existing_returnsDto() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(testReview));

        Optional<ReviewDto> result = reviewService.getReviewById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("Отличный врач!", result.get().getReviewText());
        assertEquals(100L, result.get().getAppointmentId());
    }

    @Test
    @DisplayName("Получение отзыва по ID приёма")
    void getReviewByAppointmentId_existing_returnsDto() {
        when(reviewRepository.findByAppointmentId(100L)).thenReturn(Optional.of(testReview));

        Optional<ReviewDto> result = reviewService.getReviewByAppointmentId(100L);

        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getAppointmentId());
        assertEquals(1L, result.get().getDoctorId());
        assertEquals(5L, result.get().getPatientId());
    }

    @Test
    @DisplayName("Сохранение отзыва")
    void saveReview_savesAndReturnsDto() {
        when(reviewRepository.save(testReview)).thenReturn(testReview);

        ReviewDto result = reviewService.saveReview(testReview);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals((short) 5, result.getRating());
        verify(reviewRepository, times(1)).save(testReview);
    }

    @Test
    @DisplayName("Обновление отзыва - изменение оценки и текста")
    void updateReview_existing_updatesFields() {
        Review update = new Review();
        update.setRating((short) 3);
        update.setReviewText("Обновлённый отзыв");

        when(reviewRepository.findById(1L)).thenReturn(Optional.of(testReview));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        Optional<ReviewDto> result = reviewService.updateReview(1L, update);

        assertTrue(result.isPresent());
        assertEquals((short) 3, result.get().getRating());
        assertEquals("Обновлённый отзыв", result.get().getReviewText());
        verify(reviewRepository).save(any(Review.class));
    }
}
