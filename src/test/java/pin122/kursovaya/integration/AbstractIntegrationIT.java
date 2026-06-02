package pin122.kursovaya.integration;

import com.redis.testcontainers.RedisContainer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.Role;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.NotificationRepository;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.QueueEntryRepository;
import pin122.kursovaya.repository.ReviewRepository;
import pin122.kursovaya.repository.RoleRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.utils.EncryptPassword;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Базовый класс интеграционных тестов.
 * Testcontainers при доступном Docker, иначе embedded PostgreSQL + embedded Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationIT {

    private static final AtomicLong UNIQUE = new AtomicLong(System.nanoTime());
    private static final boolean USE_DOCKER = DockerClientFactory.instance().isDockerAvailable();

    private static PostgreSQLContainer<?> dockerPostgres;
    private static RedisContainer dockerRedis;
    private static EmbeddedPostgres embeddedPostgres;
    private static RedisServer embeddedRedis;
    private static int embeddedRedisPort;

    static {
        if (USE_DOCKER) {
            dockerPostgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("clinic_test")
                    .withUsername("test")
                    .withPassword("test");
            dockerPostgres.start();
            dockerRedis = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME);
            dockerRedis.start();
        } else {
            try {
                embeddedPostgres = EmbeddedPostgres.builder().start();
                embeddedRedisPort = findFreePort();
                embeddedRedis = RedisServer.newRedisServer()
                        .bind("127.0.0.1")
                        .port(embeddedRedisPort)
                        .build();
                embeddedRedis.start();
            } catch (IOException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        if (USE_DOCKER) {
            registry.add("spring.datasource.url", dockerPostgres::getJdbcUrl);
            registry.add("spring.datasource.username", dockerPostgres::getUsername);
            registry.add("spring.datasource.password", dockerPostgres::getPassword);
            registry.add("spring.data.redis.host", dockerRedis::getHost);
            registry.add("spring.data.redis.port", () -> String.valueOf(dockerRedis.getMappedPort(6379)));
        } else {
            registry.add("spring.datasource.url",
                    () -> embeddedPostgres.getJdbcUrl("postgres", "postgres"));
            registry.add("spring.datasource.username", () -> "postgres");
            registry.add("spring.datasource.password", () -> "postgres");
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.data.redis.host", () -> "127.0.0.1");
            registry.add("spring.data.redis.port", () -> String.valueOf(embeddedRedisPort));
        }
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected DoctorRepository doctorRepository;

    @Autowired
    protected AppointmentRepository appointmentRepository;

    @Autowired
    protected QueueEntryRepository queueEntryRepository;

    @Autowired
    protected ReviewRepository reviewRepository;

    @Autowired
    protected PatientRepository patientRepository;

    @Autowired
    protected NotificationRepository notificationRepository;

    @Autowired
    protected RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void resetIntegrationState() {
        flushRedis();
        clearDatabase();
        ensureDefaultRoles();
    }

    protected void flushRedis() {
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    protected void clearDatabase() {
        queueEntryRepository.deleteAll();
        notificationRepository.deleteAll();
        reviewRepository.deleteAll();
        appointmentRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected void ensureDefaultRoles() {
        saveRoleIfMissing("patient", "Пациент");
        saveRoleIfMissing("doctor", "Врач");
        saveRoleIfMissing("admin", "Администратор");
    }

    protected Role saveRoleIfMissing(String code, String name) {
        return roleRepository.findByCode(code).orElseGet(() -> {
            Role role = new Role();
            role.setCode(code);
            role.setName(name);
            return roleRepository.save(role);
        });
    }

    protected String uniqueEmail(String prefix) {
        return prefix + "+" + UNIQUE.incrementAndGet() + "@it.test";
    }

    protected String uniquePhone() {
        long n = UNIQUE.incrementAndGet() % 1_000_000_000L;
        return "+7900" + String.format("%07d", n);
    }

    protected User createPatientUser(String email, String rawPassword) {
        Role patientRole = roleRepository.findByCode("patient").orElseThrow();
        User user = new User();
        user.setEmail(email);
        user.setPhone(uniquePhone());
        user.setFirstName("Иван");
        user.setLastName("Пациентов");
        user.setPasswordHash(EncryptPassword.hashPassword(rawPassword));
        user.setRole(patientRole);
        user.setActive(true);

        Patient patient = new Patient();
        patient.setUser(user);
        patient.setBirthDate(LocalDate.of(1990, 5, 15));
        patient.setGender((short) 1);
        patient.setInsuranceNumber("IT-" + UNIQUE.incrementAndGet());
        user.setPatient(patient);

        return userRepository.save(user);
    }

    protected User createDoctorUser(String email, String rawPassword) {
        Role doctorRole = roleRepository.findByCode("doctor").orElseThrow();
        User user = new User();
        user.setEmail(email);
        user.setPhone(uniquePhone());
        user.setFirstName("Анна");
        user.setLastName("Докторова");
        user.setPasswordHash(EncryptPassword.hashPassword(rawPassword));
        user.setRole(doctorRole);
        user.setActive(true);

        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setExperienceYears(8);
        doctor.setBio("Integration test doctor");
        user.setDoctor(doctor);

        return userRepository.save(user);
    }

    protected Appointment createSlot(Doctor doctor, OffsetDateTime startTime, String status) {
        Appointment appointment = new Appointment();
        appointment.setDoctor(doctor);
        appointment.setStartTime(startTime);
        appointment.setEndTime(startTime.plusMinutes(30));
        appointment.setStatus(status);
        appointment.setSource("online");
        appointment.setCreatedAt(OffsetDateTime.now());
        appointment.setUpdatedAt(OffsetDateTime.now());
        return appointmentRepository.save(appointment);
    }

    protected OffsetDateTime futureSlotTime(int plusHours) {
        return OffsetDateTime.now(ZoneId.systemDefault()).plusHours(plusHours);
    }

    protected LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    protected Long requirePatientId(User user) {
        return Objects.requireNonNull(user.getPatient(), "patient profile required").getId();
    }

    protected Doctor requireDoctor(User user) {
        return Objects.requireNonNull(user.getDoctor(), "doctor profile required");
    }
}
