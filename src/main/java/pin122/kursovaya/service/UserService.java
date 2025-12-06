package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
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
import pin122.kursovaya.utils.SecurityUtils;

import java.util.List;
import java.util.Optional;
import java.util.HashMap;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;
    private final QueueEntryRepository queueEntryRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       AppointmentRepository appointmentRepository,
                       ReviewRepository reviewRepository,
                       QueueEntryRepository queueEntryRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.appointmentRepository = appointmentRepository;
        this.reviewRepository = reviewRepository;
        this.queueEntryRepository = queueEntryRepository;
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserDto::new)
                .toList();
    }

    public Optional<UserDto> getUserById(Long id) {
        return userRepository.findById(id)
                .map(UserDto::new);
    }

    public Optional<UserDto> getCurrentUser() {
        return SecurityUtils.getCurrentUser(userRepository)
                .map(UserDto::new);
    }

    public Optional<CurrentUserDto> getCurrentUserWithIds() {
        Optional<String> emailOpt = SecurityUtils.getCurrentUserEmail();
        if (emailOpt.isEmpty()) {
            return Optional.empty();
        }
        User user = userRepository.findByEmailWithPatientAndDoctor(emailOpt.get());
        return user != null ? Optional.of(new CurrentUserDto(user)) : Optional.empty();
    }

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
           usr.setPhone(userDto.getPhone());
           
           // Гибкая обработка ФИО
           if (fio.length >= 3) {
               // Фамилия Имя Отчество
               usr.setLastName(fio[0]);
               usr.setFirstName(fio[1]);
               usr.setMiddleName(fio[2]);
           } else if (fio.length == 2) {
               // Фамилия Имя
               usr.setLastName(fio[0]);
               usr.setFirstName(fio[1]);
               usr.setMiddleName(null);
           } else if (fio.length == 1) {
               // Только имя
               usr.setFirstName(fio[0]);
               usr.setLastName(null);
               usr.setMiddleName(null);
           } else {
               // Пустая строка
               usr.setFirstName(null);
               usr.setLastName(null);
               usr.setMiddleName(null);
           }
           
           usr.setPasswordHash(EncryptPassword.hashPassword(userDto.getPassword()));
           Patient patient = new Patient();
           patient.setUser(usr);
           usr.setPatient(patient);
           // назначаем роль по умолчанию "patient"
           roleRepository.findByCode("patient").ifPresent(role -> usr.getRoles().add(role));
           User createdUsr = userRepository.save(usr);
           return Optional.of(new UserDto(createdUsr));
       }
    }

    public User saveUser(UserDto user) {
        return userRepository.save(new User(user));
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

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
        user.setPhone(dto.getPhone());

        // Гибкая обработка ФИО
        if (fio.length >= 3) {
            // Фамилия Имя Отчество
            user.setLastName(fio[0]);
            user.setFirstName(fio[1]);
            user.setMiddleName(fio[2]);
        } else if (fio.length == 2) {
            // Фамилия Имя
            user.setLastName(fio[0]);
            user.setFirstName(fio[1]);
            user.setMiddleName(null);
        } else if (fio.length == 1) {
            // Только имя
            user.setFirstName(fio[0]);
            user.setLastName(null);
            user.setMiddleName(null);
        } else {
            // Пустая строка
            user.setFirstName(null);
            user.setLastName(null);
            user.setMiddleName(null);
        }

        user.setPasswordHash(EncryptPassword.hashPassword(dto.getPassword()));

        // Создаем пациента с полными данными
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setBirthDate(dto.getBirthDate());
        patient.setGender(dto.getGender());
        patient.setInsuranceNumber(dto.getInsuranceNumber());

        user.setPatient(patient);

        // Назначаем роль по умолчанию "patient"
        roleRepository.findByCode("patient").ifPresent(role -> user.getRoles().add(role));

        User savedUser = userRepository.save(user);
        Patient savedPatient = savedUser.getPatient();

        // Создаем DTO для ответа
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

    public Optional<UserStatsDto> getUserStats() {
        Optional<CurrentUserDto> currentUserOpt = getCurrentUserWithIds();
        if (currentUserOpt.isEmpty()) {
            return Optional.empty();
        }

        CurrentUserDto currentUser = currentUserOpt.get();
        if (currentUser.getPatientId() == null) {
            // Если пользователь не является пациентом, возвращаем нулевую статистику
            return Optional.of(new UserStatsDto(0L, 0L, 0L));
        }

        Long appointmentsCount = appointmentRepository.countByPatientId(currentUser.getPatientId());
        Long reviewsCount = reviewRepository.countByPatientId(currentUser.getPatientId());
        Long queueEntriesCount = queueEntryRepository.countByPatientId(currentUser.getPatientId());

        return Optional.of(new UserStatsDto(appointmentsCount, reviewsCount, queueEntriesCount));
    }
}