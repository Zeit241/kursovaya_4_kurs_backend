package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pin122.kursovaya.dto.CreateUserDto;
import pin122.kursovaya.dto.CreateUserWithPatientDto;
import pin122.kursovaya.dto.CurrentUserDto;
import pin122.kursovaya.dto.PatientDto;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.dto.UserStatsDto;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.QueueEntryRepository;
import pin122.kursovaya.repository.ReviewRepository;
import pin122.kursovaya.repository.RoleRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.utils.EncryptPassword;
import pin122.kursovaya.utils.FormatUtils;
import pin122.kursovaya.utils.SecurityUtils;

import java.util.List;
import java.util.Optional;

/**
 * Операции с пользователями: список, текущий пользователь, регистрация, сохранение, статистика по пациенту.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final CurrentUserDtoFactory currentUserDtoFactory;

    /**
     * @param userRepository          репозиторий пользователей
     * @param roleRepository          справочник ролей
     * @param appointmentRepository   записи на приём (для статистики)
     * @param reviewRepository        отзывы (для статистики)
     * @param queueEntryRepository    очередь (для статистики)
     * @param currentUserDtoFactory   фабрика {@link CurrentUserDto} с дозаполнением doctorId/patientId
     */
    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       AppointmentRepository appointmentRepository,
                       ReviewRepository reviewRepository,
                       QueueEntryRepository queueEntryRepository,
                       CurrentUserDtoFactory currentUserDtoFactory) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.appointmentRepository = appointmentRepository;
        this.reviewRepository = reviewRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.currentUserDtoFactory = currentUserDtoFactory;
    }

    /**
     * Возвращает всех пользователей в виде {@link UserDto}.
     *
     * @return список DTO
     */
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserDto::new)
                .toList();
    }

    /**
     * Ищет пользователя по идентификатору.
     *
     * @param id первичный ключ
     * @return {@link UserDto}, если найден
     */
    public Optional<UserDto> getUserById(Long id) {
        return userRepository.findById(id)
                .map(UserDto::new);
    }

    /**
     * Возвращает текущего аутентифицированного пользователя (без дозаполнения patientId/doctorId).
     *
     * @return {@link UserDto} из {@link SecurityUtils#getCurrentUser(UserRepository)}
     */
    public Optional<UserDto> getCurrentUser() {
        return SecurityUtils.getCurrentUser(userRepository)
                .map(UserDto::new);
    }

    /**
     * Загружает текущего пользователя с профилями врача/пациента и собирает {@link CurrentUserDto}.
     *
     * @return непустой {@link Optional}, если в контексте безопасности есть email и пользователь найден в БД
     */
    @Transactional(readOnly = true)
    public Optional<CurrentUserDto> getCurrentUserWithIds() {
        Optional<String> emailOpt = SecurityUtils.getCurrentUserEmail();
        if (emailOpt.isEmpty()) {
            return Optional.empty();
        }
        User user = userRepository.findByEmailWithPatientAndDoctor(emailOpt.get());
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(currentUserDtoFactory.build(user));
    }

    /**
     * Регистрирует пользователя с ролью patient и вложенной сущностью {@link Patient}, если email/телефон уникальны.
     *
     * @param userDto данные регистрации (ФИО одной строкой, email, телефон, пароль)
     * @return созданный {@link UserDto} или пустой {@link Optional}, если пользователь с таким email или телефоном уже есть
     */
    public Optional<UserDto> createUser(CreateUserDto userDto) {
       Optional<User> user = userRepository.findByEmailOrPhone(userDto.getEmail(), userDto.getPhone());

       if (user.isPresent()) {
           System.out.println("User already exists");
           return Optional.empty();
       }else{
           System.out.println("Creating user");
           User usr = new User();
           String[] fio = userDto.getFio().trim().split("\\s+");
           usr.setEmail(userDto.getEmail());
           usr.setPhone(FormatUtils.normalizePhone(userDto.getPhone()));

           if (fio.length >= 3) {
               usr.setLastName(fio[0]);
               usr.setFirstName(fio[1]);
               usr.setMiddleName(fio[2]);
           } else if (fio.length == 2) {
               usr.setLastName(fio[0]);
               usr.setFirstName(fio[1]);
               usr.setMiddleName(null);
           } else if (fio.length == 1) {
               usr.setFirstName(fio[0]);
               usr.setLastName(null);
               usr.setMiddleName(null);
           } else {
               usr.setFirstName(null);
               usr.setLastName(null);
               usr.setMiddleName(null);
           }

           usr.setPasswordHash(EncryptPassword.hashPassword(userDto.getPassword()));
           Patient patient = new Patient();
           patient.setUser(usr);
           usr.setPatient(patient);
           roleRepository.findByCode("patient").ifPresent(usr::setRole);
           User createdUsr = userRepository.save(usr);
           return Optional.of(new UserDto(createdUsr));
       }
    }

    /**
     * Сохраняет пользователя из DTO (конструктор сущности {@link User} из {@link UserDto}).
     *
     * @param user DTO с данными для сохранения
     * @return сохранённая сущность {@link User}
     */
    public User saveUser(UserDto user) {
        return userRepository.save(new User(user));
    }

    /**
     * Удаляет пользователя по идентификатору.
     *
     * @param id первичный ключ
     */
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * Создаёт пользователя и полноценного пациента при уникальном email/телефоне.
     *
     * @param dto email, телефон, пароль, ФИО и медицинские поля пациента
     * @return {@link PatientDto} с вложенным {@link UserDto} или пустой {@link Optional}, если дубликат
     */
    public Optional<PatientDto> createUserWithPatient(CreateUserWithPatientDto dto) {
        Optional<User> existingUser = userRepository.findByEmailOrPhone(dto.getEmail(), dto.getPhone());

        if (existingUser.isPresent()) {
            System.out.println("User already exists");
            return Optional.empty();
        }

        System.out.println("Creating user with patient");
        User user = new User();
        String[] fio = dto.getFio().trim().split("\\s+");
        user.setEmail(dto.getEmail());
        user.setPhone(FormatUtils.normalizePhone(dto.getPhone()));

        if (fio.length >= 3) {
            user.setLastName(fio[0]);
            user.setFirstName(fio[1]);
            user.setMiddleName(fio[2]);
        } else if (fio.length == 2) {
            user.setLastName(fio[0]);
            user.setFirstName(fio[1]);
            user.setMiddleName(null);
        } else if (fio.length == 1) {
            user.setFirstName(fio[0]);
            user.setLastName(null);
            user.setMiddleName(null);
        } else {
            user.setFirstName(null);
            user.setLastName(null);
            user.setMiddleName(null);
        }

        user.setPasswordHash(EncryptPassword.hashPassword(dto.getPassword()));

        Patient patient = new Patient();
        patient.setUser(user);
        patient.setBirthDate(dto.getBirthDate());
        patient.setGender(dto.getGender());
        patient.setInsuranceNumber(FormatUtils.normalizeInsuranceNumber(dto.getInsuranceNumber()));

        user.setPatient(patient);

        roleRepository.findByCode("patient").ifPresent(user::setRole);

        User savedUser = userRepository.save(user);
        Patient savedPatient = savedUser.getPatient();

        PatientDto patientDto = new PatientDto();
        patientDto.setId(savedPatient.getId());
        patientDto.setUser(new UserDto(savedUser));
        patientDto.setBirthDate(savedPatient.getBirthDate());
        patientDto.setGender(savedPatient.getGender());
        patientDto.setInsuranceNumber(savedPatient.getInsuranceNumber());
        patientDto.setCreatedAt(savedPatient.getCreatedAt());
        patientDto.setUpdatedAt(savedPatient.getUpdatedAt());

        return Optional.of(patientDto);
    }

    /**
     * Считает для текущего пользователя-пациента количество приёмов, отзывов и записей в очереди.
     *
     * @return {@link UserStatsDto} или пустой {@link Optional}, если профиль текущего пользователя недоступен;
     *         для не-пациента возвращаются нулевые счётчики
     */
    public Optional<UserStatsDto> getUserStats() {
        Optional<CurrentUserDto> currentUserOpt = getCurrentUserWithIds();
        if (currentUserOpt.isEmpty()) {
            return Optional.empty();
        }

        CurrentUserDto currentUser = currentUserOpt.get();
        if (currentUser.getPatientId() == null) {
            return Optional.of(new UserStatsDto(0L, 0L, 0L));
        }

        Long appointmentsCount = appointmentRepository.countByPatientId(currentUser.getPatientId());
        Long reviewsCount = reviewRepository.countByPatientId(currentUser.getPatientId());
        Long queueEntriesCount = queueEntryRepository.countByPatientId(currentUser.getPatientId());

        return Optional.of(new UserStatsDto(appointmentsCount, reviewsCount, queueEntriesCount));
    }
}
