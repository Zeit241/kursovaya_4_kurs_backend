package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pin122.kursovaya.dto.CurrentUserDto;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.Role;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.PatientRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Тесты для CurrentUserDtoFactory — сборка CurrentUserDto с дозаполнением doctorId/patientId.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurrentUserDtoFactory - тесты фабрики профиля текущего пользователя")
class CurrentUserDtoFactoryTest {

    private static final String DIRECTUS_URL = "http://localhost:8055";

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private CurrentUserDtoFactory factory;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(factory, "directusPublicUrl", DIRECTUS_URL);
    }

    @Test
    @DisplayName("build(null) возвращает null")
    void build_nullUser_returnsNull() {
        assertNull(factory.build(null));

        verifyNoInteractions(doctorRepository, patientRepository);
    }

    @Test
    @DisplayName("build копирует базовые поля пользователя в DTO")
    void build_userWithAdminRole_mapsBasicFields() {
        Role role = roleWithCode("admin");
        User user = userWithRole(1L, "admin@test.com", "Анна", "Админова", role);

        CurrentUserDto dto = factory.build(user);

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("admin@test.com", dto.getEmail());
        assertEquals("Анна", dto.getFirstName());
        assertEquals("Админова", dto.getLastName());
        assertTrue(dto.isActive());
        assertNull(dto.getDoctorId());
        assertNull(dto.getPatientId());

        verifyNoInteractions(doctorRepository, patientRepository);
    }

    @Test
    @DisplayName("build без роли не обращается к репозиториям")
    void build_userWithoutRole_skipsEnrichment() {
        User user = new User();
        user.setId(5L);
        user.setEmail("norole@test.com");

        CurrentUserDto dto = factory.build(user);

        assertNotNull(dto);
        assertEquals(5L, dto.getId());
        assertNull(dto.getDoctorId());
        assertNull(dto.getPatientId());

        verifyNoInteractions(doctorRepository, patientRepository);
    }

    @Test
    @DisplayName("build с пустым role.code не обращается к репозиториям")
    void build_userWithNullRoleCode_skipsEnrichment() {
        Role role = new Role();
        role.setCode(null);
        User user = userWithRole(6L, "emptyrole@test.com", "Пётр", "Пустой", role);

        CurrentUserDto dto = factory.build(user);

        assertNotNull(dto);
        assertNull(dto.getDoctorId());
        assertNull(dto.getPatientId());

        verifyNoInteractions(doctorRepository, patientRepository);
    }

    @Test
    @DisplayName("build для роли doctor подставляет doctorId и DoctorInfo из БД")
    void build_doctorRole_enrichesFromRepository() {
        Role role = roleWithCode("doctor");
        User user = userWithRole(10L, "doctor@test.com", "Иван", "Докторов", role);

        Doctor doctor = new Doctor();
        doctor.setId(100L);
        doctor.setUser(user);
        doctor.setBio("Опытный врач");

        when(doctorRepository.findByUserId(10L)).thenReturn(Optional.of(doctor));

        CurrentUserDto dto = factory.build(user);

        assertEquals(100L, dto.getDoctorId());
        assertNotNull(dto.getDoctor());
        assertEquals(100L, dto.getDoctor().getId());
        assertEquals("Опытный врач", dto.getDoctor().getBio());

        verify(doctorRepository).findByUserId(10L);
        verifyNoInteractions(patientRepository);
    }

    @Test
    @DisplayName("build для роли doctor без строки в doctors оставляет doctorId пустым")
    void build_doctorRole_missingProfile_keepsNullDoctorId() {
        Role role = roleWithCode("DOCTOR");
        User user = userWithRole(11L, "missing-doctor@test.com", "Сергей", "НетПрофиля", role);

        when(doctorRepository.findByUserId(11L)).thenReturn(Optional.empty());

        CurrentUserDto dto = factory.build(user);

        assertNull(dto.getDoctorId());
        assertNull(dto.getDoctor());

        verify(doctorRepository).findByUserId(11L);
        verifyNoInteractions(patientRepository);
    }

    @Test
    @DisplayName("build не обращается к DoctorRepository, если doctorId уже заполнен из User")
    void build_doctorWithLoadedLink_skipsRepository() {
        Role role = roleWithCode("doctor");
        User user = userWithRole(12L, "loaded-doctor@test.com", "Олег", "Загруженный", role);

        Doctor doctor = new Doctor();
        doctor.setId(120L);
        doctor.setUser(user);
        user.setDoctor(doctor);

        CurrentUserDto dto = factory.build(user);

        assertEquals(120L, dto.getDoctorId());
        assertNotNull(dto.getDoctor());

        verifyNoInteractions(doctorRepository, patientRepository);
    }

    @Test
    @DisplayName("build для роли patient подставляет patientId и PatientInfo из БД")
    void build_patientRole_enrichesFromRepository() {
        Role role = roleWithCode("patient");
        User user = userWithRole(20L, "patient@test.com", "Мария", "Пациентова", role);

        Patient patient = new Patient();
        patient.setId(200L);
        patient.setUser(user);
        patient.setInsuranceNumber("POL-12345");

        when(patientRepository.findByUserId(20L)).thenReturn(Optional.of(patient));

        CurrentUserDto dto = factory.build(user);

        assertEquals(200L, dto.getPatientId());
        assertNotNull(dto.getPatient());
        assertEquals(200L, dto.getPatient().getId());
        assertEquals("POL-12345", dto.getPatient().getInsuranceNumber());

        verify(patientRepository).findByUserId(20L);
        verifyNoInteractions(doctorRepository);
    }

    private static Role roleWithCode(String code) {
        Role role = new Role();
        role.setId(1L);
        role.setCode(code);
        return role;
    }

    private static User userWithRole(Long id, String email, String firstName, String lastName, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setActive(true);
        user.setRole(role);
        return user;
    }
}
