package pin122.kursovaya.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pin122.kursovaya.dto.UserDto;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для модели User
 */
@DisplayName("User - тесты модели пользователя")
class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setPhone("+79001234567");
        user.setFirstName("Иван");
        user.setLastName("Петров");
        user.setMiddleName("Сергеевич");
        user.setPasswordHash("hashedPassword123");
        user.setActive(true);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Создание пользователя с конструктором по умолчанию")
    void defaultConstructor_createsEmptyUser() {
        User newUser = new User();
        
        assertNotNull(newUser);
        assertNull(newUser.getId());
        assertNull(newUser.getEmail());
        assertTrue(newUser.isActive());
        assertNotNull(newUser.getCreatedAt());
        assertNotNull(newUser.getUpdatedAt());
    }

    @Test
    @DisplayName("Создание пользователя из UserDto")
    void constructorFromDto_copiesAllFields() {
        UserDto dto = new UserDto(
                2L,
                "dto@example.com",
                "+79009999999",
                "Мария",
                "Сидорова",
                "Ивановна",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                true
        );

        User userFromDto = new User(dto);

        assertEquals(dto.getId(), userFromDto.getId());
        assertEquals(dto.getEmail(), userFromDto.getEmail());
        assertEquals(dto.getPhone(), userFromDto.getPhone());
        assertEquals(dto.getFirstName(), userFromDto.getFirstName());
        assertEquals(dto.getLastName(), userFromDto.getLastName());
        assertEquals(dto.getMiddleName(), userFromDto.getMiddleName());
    }

    @Test
    @DisplayName("Проверка геттеров и сеттеров")
    void gettersAndSetters_workCorrectly() {
        assertEquals(1L, user.getId());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("+79001234567", user.getPhone());
        assertEquals("Иван", user.getFirstName());
        assertEquals("Петров", user.getLastName());
        assertEquals("Сергеевич", user.getMiddleName());
        assertEquals("hashedPassword123", user.getPasswordHash());
        assertTrue(user.isActive());
    }

    @Test
    @DisplayName("Установка и получение ролей")
    void roles_canBeSetAndRetrieved() {
        Role adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setCode("admin");
        adminRole.setName("Администратор");

        Role patientRole = new Role();
        patientRole.setId(2L);
        patientRole.setCode("patient");
        patientRole.setName("Пациент");

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        roles.add(patientRole);

        user.setRoles(roles);

        assertNotNull(user.getRoles());
        assertEquals(2, user.getRoles().size());
        assertTrue(user.getRoles().contains(adminRole));
        assertTrue(user.getRoles().contains(patientRole));
    }

    @Test
    @DisplayName("Добавление роли к пользователю")
    void addRole_addsRoleToSet() {
        Role doctorRole = new Role();
        doctorRole.setId(3L);
        doctorRole.setCode("doctor");
        doctorRole.setName("Врач");

        user.getRoles().add(doctorRole);

        assertEquals(1, user.getRoles().size());
        assertTrue(user.getRoles().contains(doctorRole));
    }

    @Test
    @DisplayName("Связь с Patient - установка и получение")
    void patientRelation_canBeSetAndRetrieved() {
        Patient patient = new Patient();
        patient.setId(1L);
        patient.setUser(user);
        patient.setInsuranceNumber("123-456-789-04");

        user.setPatient(patient);

        assertNotNull(user.getPatient());
        assertEquals(patient, user.getPatient());
        assertEquals("123-456-789-04", user.getPatient().getInsuranceNumber());
    }

    @Test
    @DisplayName("Связь с Doctor - установка и получение")
    void doctorRelation_canBeSetAndRetrieved() {
        Doctor doctor = new Doctor();
        doctor.setId(1L);
        doctor.setUser(user);
        doctor.setDisplayName("Д-р Петров");
        doctor.setExperienceYears(10);

        user.setDoctor(doctor);

        assertNotNull(user.getDoctor());
        assertEquals(doctor, user.getDoctor());
        assertEquals("Д-р Петров", user.getDoctor().getDisplayName());
    }

    @Test
    @DisplayName("Изменение активности пользователя")
    void setActive_changesActiveStatus() {
        assertTrue(user.isActive());
        
        user.setActive(false);
        
        assertFalse(user.isActive());
        
        user.setActive(true);
        
        assertTrue(user.isActive());
    }

    @Test
    @DisplayName("Обновление updatedAt")
    void setUpdatedAt_updatesTimestamp() {
        OffsetDateTime originalUpdatedAt = user.getUpdatedAt();
        OffsetDateTime newUpdatedAt = OffsetDateTime.now().plusDays(1);
        
        user.setUpdatedAt(newUpdatedAt);
        
        assertNotEquals(originalUpdatedAt, user.getUpdatedAt());
        assertEquals(newUpdatedAt, user.getUpdatedAt());
    }

    @Test
    @DisplayName("Проверка equals и hashCode - без связанных сущностей")
    void equalsAndHashCode_workWithoutRelations() {
        User user1 = new User();
        user1.setId(1L);
        user1.setEmail("test@test.com");

        User user2 = new User();
        user2.setId(1L);
        user2.setEmail("test@test.com");

        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    @DisplayName("Пользователь с null ФИО")
    void userWithNullNames_works() {
        User userWithNullNames = new User();
        userWithNullNames.setEmail("nullnames@test.com");
        userWithNullNames.setFirstName(null);
        userWithNullNames.setLastName(null);
        userWithNullNames.setMiddleName(null);

        assertNull(userWithNullNames.getFirstName());
        assertNull(userWithNullNames.getLastName());
        assertNull(userWithNullNames.getMiddleName());
    }
}
