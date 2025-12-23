package pin122.kursovaya.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import pin122.kursovaya.dto.DoctorDto;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.DoctorSpecialization;
import pin122.kursovaya.model.Specialization;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для DoctorService - сервис работы с врачами
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DoctorService - тесты сервиса врачей")
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private SpecializationRepository specializationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private QueueEntryRepository queueEntryRepository;

    @InjectMocks
    private DoctorService doctorService;

    private Doctor testDoctor;
    private User doctorUser;

    @BeforeEach
    void setUp() {
        doctorUser = new User();
        doctorUser.setId(1L);
        doctorUser.setEmail("doctor@clinic.com");
        doctorUser.setPhone("+79001234567");
        doctorUser.setFirstName("Алексей");
        doctorUser.setLastName("Врачев");
        doctorUser.setMiddleName("Петрович");
        doctorUser.setActive(true);
        doctorUser.setCreatedAt(OffsetDateTime.now());
        doctorUser.setUpdatedAt(OffsetDateTime.now());

        testDoctor = new Doctor();
        testDoctor.setId(1L);
        testDoctor.setUser(doctorUser);
        testDoctor.setDisplayName("Д-р Врачев А.П.");
        testDoctor.setBio("Опытный терапевт");
        testDoctor.setExperienceYears(15);
        testDoctor.setCreatedAt(OffsetDateTime.now());
        testDoctor.setUpdatedAt(OffsetDateTime.now());
        testDoctor.setSpecializations(new ArrayList<>());
    }

    @Test
    @DisplayName("Получение всех врачей без пагинации")
    void getAllDoctors_withoutPagination_returnsAllDoctors() {
        Doctor doctor2 = createAnotherDoctor();
        
        when(doctorRepository.findAll()).thenReturn(Arrays.asList(testDoctor, doctor2));
        when(reviewRepository.findAverageRatingByDoctorId(anyLong())).thenReturn(Optional.of(4.5));
        when(reviewRepository.countByDoctorId(anyLong())).thenReturn(10L);

        List<DoctorDto> result = doctorService.getAllDoctors(null, null, null, null);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(doctorRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Получение всех врачей с лимитом")
    void getAllDoctors_withLimit_returnsLimitedDoctors() {
        Page<Doctor> page = new PageImpl<>(List.of(testDoctor));
        
        when(doctorRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(reviewRepository.findAverageRatingByDoctorId(anyLong())).thenReturn(Optional.of(4.0));
        when(reviewRepository.countByDoctorId(anyLong())).thenReturn(5L);

        List<DoctorDto> result = doctorService.getAllDoctors(1, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Получение врача по ID - найден")
    void getDoctorById_existing_returnsDoctorDto() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(reviewRepository.findAverageRatingByDoctorId(1L)).thenReturn(Optional.of(4.8));
        when(reviewRepository.countByDoctorId(1L)).thenReturn(25L);

        Optional<DoctorDto> result = doctorService.getDoctorById(1L);

        assertTrue(result.isPresent());
        assertEquals("Д-р Врачев А.П.", result.get().getDisplayName());
        assertEquals(15, result.get().getExperienceYears());
        assertEquals(4.8, result.get().getRating());
        assertEquals(25, result.get().getReviewCount());
    }

    @Test
    @DisplayName("Получение врача по ID - не найден")
    void getDoctorById_notExisting_returnsEmpty() {
        when(doctorRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<DoctorDto> result = doctorService.getDoctorById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Поиск врачей по имени или специализации")
    void searchDoctors_byQuery_returnsMatchingDoctors() {
        when(doctorRepository.searchByFullNameOrSpecialization("терапевт"))
                .thenReturn(List.of(testDoctor));
        when(reviewRepository.findAverageRatingByDoctorId(anyLong())).thenReturn(Optional.of(4.5));
        when(reviewRepository.countByDoctorId(anyLong())).thenReturn(10L);

        List<DoctorDto> result = doctorService.searchDoctors("терапевт", null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Д-р Врачев А.П.", result.get(0).getDisplayName());
    }

    @Test
    @DisplayName("Удаление врача - каскадное удаление")
    void deleteDoctor_cascadesDeletes() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(appointmentRepository.findByDoctorId(1L)).thenReturn(Collections.emptyList());
        when(scheduleRepository.findByDoctorId(1L)).thenReturn(Collections.emptyList());
        when(queueEntryRepository.findByDoctorIdOrderByPositionAsc(1L)).thenReturn(Collections.emptyList());
        when(reviewRepository.findByDoctorId(1L)).thenReturn(Collections.emptyList());

        doctorService.deleteDoctor(1L);

        verify(doctorRepository, times(1)).delete(testDoctor);
    }

    @Test
    @DisplayName("Удаление несуществующего врача - ничего не происходит")
    void deleteDoctor_notExisting_doesNothing() {
        when(doctorRepository.findById(999L)).thenReturn(Optional.empty());

        doctorService.deleteDoctor(999L);

        verify(doctorRepository, never()).delete(any(Doctor.class));
    }

    @Test
    @DisplayName("Получение врачей с сортировкой по рейтингу (убывание)")
    void getAllDoctors_sortByRatingDesc_sortedCorrectly() {
        Doctor doctor2 = createAnotherDoctor();
        
        when(doctorRepository.findAll()).thenReturn(Arrays.asList(testDoctor, doctor2));
        when(reviewRepository.findAverageRatingByDoctorId(1L)).thenReturn(Optional.of(3.0));
        when(reviewRepository.findAverageRatingByDoctorId(2L)).thenReturn(Optional.of(5.0));
        when(reviewRepository.countByDoctorId(anyLong())).thenReturn(5L);

        List<DoctorDto> result = doctorService.getAllDoctors(null, null, "rating", "desc");

        assertNotNull(result);
        assertEquals(2, result.size());
        // Проверяем, что первый врач имеет более высокий рейтинг
        assertTrue(result.get(0).getRating() >= result.get(1).getRating());
    }

    @Test
    @DisplayName("Рейтинг округляется до 1 знака после запятой")
    void getDoctorById_ratingRoundedToOneDecimal() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(reviewRepository.findAverageRatingByDoctorId(1L)).thenReturn(Optional.of(4.567));
        when(reviewRepository.countByDoctorId(1L)).thenReturn(10L);

        Optional<DoctorDto> result = doctorService.getDoctorById(1L);

        assertTrue(result.isPresent());
        assertEquals(4.6, result.get().getRating()); // 4.567 округляется до 4.6
    }

    @Test
    @DisplayName("Врач без отзывов имеет null рейтинг")
    void getDoctorById_noReviews_ratingIsNull() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(reviewRepository.findAverageRatingByDoctorId(1L)).thenReturn(Optional.empty());
        when(reviewRepository.countByDoctorId(1L)).thenReturn(0L);

        Optional<DoctorDto> result = doctorService.getDoctorById(1L);

        assertTrue(result.isPresent());
        assertNull(result.get().getRating());
        assertEquals(0, result.get().getReviewCount());
    }

    private Doctor createAnotherDoctor() {
        User user2 = new User();
        user2.setId(2L);
        user2.setEmail("doctor2@clinic.com");
        user2.setFirstName("Мария");
        user2.setLastName("Хирургова");
        user2.setActive(true);
        user2.setCreatedAt(OffsetDateTime.now());
        user2.setUpdatedAt(OffsetDateTime.now());

        Doctor doctor2 = new Doctor();
        doctor2.setId(2L);
        doctor2.setUser(user2);
        doctor2.setDisplayName("Д-р Хирургова М.");
        doctor2.setExperienceYears(20);
        doctor2.setCreatedAt(OffsetDateTime.now());
        doctor2.setUpdatedAt(OffsetDateTime.now());
        doctor2.setSpecializations(new ArrayList<>());
        
        return doctor2;
    }
}
