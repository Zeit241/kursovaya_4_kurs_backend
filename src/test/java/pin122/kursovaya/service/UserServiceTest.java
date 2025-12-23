package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pin122.kursovaya.dto.CreateUserDto;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.model.Role;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для UserService - сервис работы с пользователями
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService - тесты сервиса пользователей")
class UserServiceTest {

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
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setPhone("+79001234567");
        testUser.setFirstName("Иван");
        testUser.setLastName("Петров");
        testUser.setMiddleName("Сергеевич");
        testUser.setActive(true);
        testUser.setCreatedAt(OffsetDateTime.now());
        testUser.setUpdatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Получение всех пользователей")
    void getAllUsers_returnsListOfUserDto() {
        User user2 = new User();
        user2.setId(2L);
        user2.setEmail("user2@example.com");
        user2.setFirstName("Мария");
        user2.setActive(true);
        user2.setCreatedAt(OffsetDateTime.now());
        user2.setUpdatedAt(OffsetDateTime.now());

        when(userRepository.findAll()).thenReturn(Arrays.asList(testUser, user2));

        List<UserDto> result = userService.getAllUsers();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("test@example.com", result.get(0).getEmail());
        assertEquals("user2@example.com", result.get(1).getEmail());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Получение пользователя по ID - найден")
    void getUserById_existingUser_returnsUserDto() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  ТЕСТ: Получение пользователя по ID                          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        
        Long userId = 1L;
        System.out.println("║  Поиск пользователя с ID: " + userId);
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        Optional<UserDto> result = userService.getUserById(userId);

        System.out.println("║  Пользователь найден: " + result.isPresent());
        System.out.println("║  Email: " + result.get().getEmail());
        System.out.println("║  Имя: " + result.get().getFirstName() + " " + result.get().getLastName());
        
        assertTrue(result.isPresent());
        assertEquals(testUser.getEmail(), result.get().getEmail());
        assertEquals(testUser.getFirstName(), result.get().getFirstName());
        
        System.out.println("║  ✅ ТЕСТ ПРОЙДЕН УСПЕШНО!");
        System.out.println("║  Пользователь корректно получен из базы данных");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    @Test
    @DisplayName("Получение пользователя по ID - не найден")
    void getUserById_nonExistingUser_returnsEmpty() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<UserDto> result = userService.getUserById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Создание пользователя - успешно")
    void createUser_newUser_createsSuccessfully() {
        CreateUserDto createDto = new CreateUserDto();
        createDto.setEmail("newuser@test.com");
        createDto.setPhone("+79009999999");
        createDto.setFio("Сидоров Алексей Иванович");
        createDto.setPassword("password123");

        Role patientRole = new Role();
        patientRole.setId(1L);
        patientRole.setCode("patient");
        patientRole.setName("Пациент");

        when(userRepository.findByEmailOrPhone(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(roleRepository.findByCode("patient"))
                .thenReturn(Optional.of(patientRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(10L);
            return savedUser;
        });

        Optional<UserDto> result = userService.createUser(createDto);

        assertTrue(result.isPresent());
        assertEquals("newuser@test.com", result.get().getEmail());
        assertEquals("Алексей", result.get().getFirstName());
        assertEquals("Сидоров", result.get().getLastName());
        assertEquals("Иванович", result.get().getMiddleName());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Создание пользователя - уже существует")
    void createUser_existingUser_returnsEmpty() {
        CreateUserDto createDto = new CreateUserDto();
        createDto.setEmail("existing@test.com");
        createDto.setPhone("+79001234567");
        createDto.setFio("Тест Тестович");
        createDto.setPassword("password");

        when(userRepository.findByEmailOrPhone(anyString(), anyString()))
                .thenReturn(Optional.of(testUser));

        Optional<UserDto> result = userService.createUser(createDto);

        assertFalse(result.isPresent());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Удаление пользователя")
    void deleteUser_callsRepository() {
        doNothing().when(userRepository).deleteById(1L);

        userService.deleteUser(1L);

        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Парсинг ФИО - только имя")
    void createUser_singleName_parsesCorrectly() {
        CreateUserDto createDto = new CreateUserDto();
        createDto.setEmail("single@test.com");
        createDto.setPhone("+79001111111");
        createDto.setFio("Александр");
        createDto.setPassword("pass");

        Role patientRole = new Role();
        patientRole.setCode("patient");

        when(userRepository.findByEmailOrPhone(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(roleRepository.findByCode("patient"))
                .thenReturn(Optional.of(patientRole));
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        Optional<UserDto> result = userService.createUser(createDto);

        assertTrue(result.isPresent());
        assertEquals("Александр", result.get().getFirstName());
        assertNull(result.get().getLastName());
        assertNull(result.get().getMiddleName());
    }

    @Test
    @DisplayName("Парсинг ФИО - имя и фамилия")
    void createUser_twoNames_parsesCorrectly() {
        CreateUserDto createDto = new CreateUserDto();
        createDto.setEmail("two@test.com");
        createDto.setPhone("+79002222222");
        createDto.setFio("Иванов Петр");
        createDto.setPassword("pass");

        Role patientRole = new Role();
        patientRole.setCode("patient");

        when(userRepository.findByEmailOrPhone(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(roleRepository.findByCode("patient"))
                .thenReturn(Optional.of(patientRole));
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        Optional<UserDto> result = userService.createUser(createDto);

        assertTrue(result.isPresent());
        assertEquals("Петр", result.get().getFirstName());
        assertEquals("Иванов", result.get().getLastName());
        assertNull(result.get().getMiddleName());
    }
}
