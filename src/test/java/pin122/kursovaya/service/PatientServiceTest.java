package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pin122.kursovaya.utils.EncryptPassword;
import pin122.kursovaya.dto.CreatePatientRequest;
import pin122.kursovaya.dto.PatientDto;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.Role;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.QueueEntryRepository;
import pin122.kursovaya.repository.ReviewRepository;
import pin122.kursovaya.repository.RoleRepository;
import pin122.kursovaya.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для PatientService - сервис работы с пациентами
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PatientService - тесты сервиса пациентов")
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private QueueEntryRepository queueEntryRepository;

    @InjectMocks
    private PatientService patientService;

    private Patient testPatient;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("patient@test.com");
        testUser.setPhone("+79001234567");
        testUser.setFirstName("Иван");
        testUser.setLastName("Петров");
        testUser.setActive(true);
        testUser.setCreatedAt(OffsetDateTime.now());
        testUser.setUpdatedAt(OffsetDateTime.now());

        testPatient = new Patient();
        testPatient.setId(10L);
        testPatient.setUser(testUser);
        testPatient.setBirthDate(LocalDate.of(1990, 5, 15));
        testPatient.setGender((short) 1);
        testPatient.setInsuranceNumber("1234567890123456");
        testPatient.setCreatedAt(OffsetDateTime.now());
        testPatient.setUpdatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Получение всех пациентов")
    void getAllPatients_returnsList() {
        Patient patient2 = new Patient();
        patient2.setId(11L);
        patient2.setUser(testUser);

        when(patientRepository.findAll()).thenReturn(Arrays.asList(testPatient, patient2));

        List<PatientDto> result = patientService.getAllPatients();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).getId());
        verify(patientRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Получение пациента по ID - найден")
    void getPatientById_existing_returnsDto() {
        when(patientRepository.findById(10L)).thenReturn(Optional.of(testPatient));

        Optional<PatientDto> result = patientService.getPatientById(10L);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getId());
        assertEquals("patient@test.com", result.get().getUser().getEmail());
    }

    @Test
    @DisplayName("Получение пациента по ID - не найден")
    void getPatientById_notExisting_returnsEmpty() {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<PatientDto> result = patientService.getPatientById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Сохранение пациента")
    void savePatient_savesAndReturnsDto() {
        when(patientRepository.save(testPatient)).thenReturn(testPatient);

        PatientDto result = patientService.savePatient(testPatient);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        verify(patientRepository, times(1)).save(testPatient);
    }

    @Test
    @DisplayName("Создание пациента - успешно")
    void createPatient_validRequest_createsUserAndPatient() {
        CreatePatientRequest request = new CreatePatientRequest();
        UserDto userDto = new UserDto();
        userDto.setEmail("new@test.com");
        userDto.setPhone("89001234567");
        userDto.setFirstName("Анна");
        userDto.setLastName("Сидорова");
        request.setUser(userDto);
        request.setPassword("TempPass123");
        request.setBirthDate(LocalDate.of(1985, 3, 20));
        request.setGender((short) 2);
        request.setInsuranceNumber("9876543210987654");

        Role patientRole = new Role();
        patientRole.setCode("patient");

        when(roleRepository.findByCode("patient")).thenReturn(Optional.of(patientRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> {
            Patient saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        PatientDto result = patientService.createPatient(request);

        assertNotNull(result);
        assertEquals(20L, result.getId());
        assertEquals("new@test.com", result.getUser().getEmail());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertTrue(
                EncryptPassword.verify("TempPass123", userCaptor.getValue().getPasswordHash()));
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    @DisplayName("Обновление пациента - успешно")
    void updatePatient_existing_updatesFields() {
        Patient update = new Patient();
        update.setBirthDate(LocalDate.of(1991, 1, 1));
        update.setGender((short) 2);
        update.setInsuranceNumber("1111222233334444");

        when(patientRepository.findById(10L)).thenReturn(Optional.of(testPatient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(i -> i.getArgument(0));

        Optional<PatientDto> result = patientService.updatePatient(10L, update);

        assertTrue(result.isPresent());
        assertEquals(LocalDate.of(1991, 1, 1), result.get().getBirthDate());
        assertEquals((short) 2, result.get().getGender());
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    @DisplayName("Обновление пациента - не найден")
    void updatePatient_notExisting_returnsEmpty() {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<PatientDto> result = patientService.updatePatient(999L, new Patient());

        assertFalse(result.isPresent());
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    @DisplayName("Обновление пациента - обновление данных пользователя")
    void updatePatient_withUserUpdate_updatesUserFields() {
        User userUpdate = new User();
        userUpdate.setFirstName("Пётр");
        userUpdate.setLastName("Иванов");
        userUpdate.setEmail("updated@test.com");
        userUpdate.setActive(false);

        Patient update = new Patient();
        update.setUser(userUpdate);

        when(patientRepository.findById(10L)).thenReturn(Optional.of(testPatient));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(patientRepository.save(any(Patient.class))).thenAnswer(i -> i.getArgument(0));

        Optional<PatientDto> result = patientService.updatePatient(10L, update);

        assertTrue(result.isPresent());
        assertEquals("Пётр", result.get().getUser().getFirstName());
        assertEquals("Иванов", result.get().getUser().getLastName());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Удаление пациента - каскадное удаление связей")
    void deletePatient_existing_deletesRelatedEntities() {
        when(patientRepository.findById(10L)).thenReturn(Optional.of(testPatient));
        doNothing().when(queueEntryRepository).deleteByPatientId(10L);
        doNothing().when(reviewRepository).deleteByPatientId(10L);
        doNothing().when(appointmentRepository).clearPatientFromAppointments(10L);
        doNothing().when(patientRepository).deleteById(10L);
        doNothing().when(userRepository).deleteById(1L);

        patientService.deletePatient(10L);

        verify(queueEntryRepository).deleteByPatientId(10L);
        verify(reviewRepository).deleteByPatientId(10L);
        verify(appointmentRepository).clearPatientFromAppointments(10L);
        verify(patientRepository).deleteById(10L);
        verify(userRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Удаление пациента - не найден, без действий")
    void deletePatient_notExisting_doesNothing() {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        patientService.deletePatient(999L);

        verify(queueEntryRepository, never()).deleteByPatientId(any());
        verify(patientRepository, never()).deleteById(any());
        verify(userRepository, never()).deleteById(any());
    }
}
