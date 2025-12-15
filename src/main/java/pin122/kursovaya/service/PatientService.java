package pin122.kursovaya.service;

import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pin122.kursovaya.dto.CreatePatientRequest;
import pin122.kursovaya.dto.PatientDto;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.QueueEntryRepository;
import pin122.kursovaya.repository.ReviewRepository;
import pin122.kursovaya.repository.RoleRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.utils.FormatUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;
    private final QueueEntryRepository queueEntryRepository;

    public PatientService(PatientRepository patientRepository, UserRepository userRepository, 
                         RoleRepository roleRepository, AppointmentRepository appointmentRepository,
                         ReviewRepository reviewRepository, QueueEntryRepository queueEntryRepository) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.appointmentRepository = appointmentRepository;
        this.reviewRepository = reviewRepository;
        this.queueEntryRepository = queueEntryRepository;
    }

    public List<PatientDto> getAllPatients() {
        return patientRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public Optional<PatientDto> getPatientById(Long id) {
        return patientRepository.findById(id)
                .map(this::mapToDto);
    }

    public PatientDto savePatient(Patient patient) {
        Patient saved = patientRepository.save(patient);
        return mapToDto(saved);
    }
    
    /**
     * Создаёт нового пациента с пользователем
     * @param request DTO с данными пациента и пользователя
     * @return созданный пациент в виде DTO
     */
    @Transactional
    public PatientDto createPatient(@Valid CreatePatientRequest request) {
        // Создаём пользователя
        User user = new User();
        user.setEmail(request.getUser().getEmail());
        user.setPhone(FormatUtils.normalizePhone(request.getUser().getPhone()));
        user.setFirstName(request.getUser().getFirstName());
        user.setLastName(request.getUser().getLastName());
        user.setMiddleName(request.getUser().getMiddleName());
        user.setActive(true);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        
        // Назначаем роль "patient"
        roleRepository.findByCode("patient").ifPresent(role -> user.getRoles().add(role));
        
        // Сохраняем пользователя
        User savedUser = userRepository.save(user);
        
        // Создаём пациента
        Patient patient = new Patient();
        patient.setUser(savedUser);
        patient.setBirthDate(request.getBirthDate());
        patient.setGender(request.getGender());
        patient.setInsuranceNumber(FormatUtils.normalizeInsuranceNumber(request.getInsuranceNumber()));
        patient.setCreatedAt(OffsetDateTime.now());
        patient.setUpdatedAt(OffsetDateTime.now());
        
        Patient savedPatient = patientRepository.save(patient);
        return mapToDto(savedPatient);
    }

    public Optional<PatientDto> updatePatient(Long id, Patient patientUpdate) {
        return patientRepository.findById(id).map(existingPatient -> {
            // Обновляем поля Patient
            if (patientUpdate.getBirthDate() != null) {
                existingPatient.setBirthDate(patientUpdate.getBirthDate());
            }
            if (patientUpdate.getGender() != null) {
                existingPatient.setGender(patientUpdate.getGender());
            }
            if (patientUpdate.getInsuranceNumber() != null) {
                existingPatient.setInsuranceNumber(FormatUtils.normalizeInsuranceNumber(patientUpdate.getInsuranceNumber()));
            }
            existingPatient.setUpdatedAt(java.time.OffsetDateTime.now());
            
            // Обновляем связанный User, если он присутствует в запросе
            if (patientUpdate.getUser() != null) {
                User existingUser = existingPatient.getUser();
                if (existingUser != null) {
                    User userUpdate = patientUpdate.getUser();
                    
                    // Обновляем поля User
                    if (userUpdate.getEmail() != null) {
                        existingUser.setEmail(userUpdate.getEmail());
                    }
                    if (userUpdate.getPhone() != null) {
                        existingUser.setPhone(FormatUtils.normalizePhone(userUpdate.getPhone()));
                    }
                    if (userUpdate.getFirstName() != null) {
                        existingUser.setFirstName(userUpdate.getFirstName());
                    }
                    if (userUpdate.getLastName() != null) {
                        existingUser.setLastName(userUpdate.getLastName());
                    }
                    if (userUpdate.getMiddleName() != null) {
                        existingUser.setMiddleName(userUpdate.getMiddleName());
                    }
                    // Обновляем active, если передан
                    existingUser.setActive(userUpdate.isActive());
                    existingUser.setUpdatedAt(java.time.OffsetDateTime.now());
                    
                    // Сохраняем обновленный User
                    userRepository.save(existingUser);
                }
            }
            
            Patient saved = patientRepository.save(existingPatient);
            return mapToDto(saved);
        });
    }

    /**
     * Удаляет пациента и все связанные с ним записи
     * @param id ID пациента
     */
    @Transactional
    public void deletePatient(Long id) {
        // Проверяем, существует ли пациент
        Patient patient = patientRepository.findById(id).orElse(null);
        if (patient == null) {
            return;
        }
        
        // Удаляем записи в очереди
        queueEntryRepository.deleteByPatientId(id);
        
        // Удаляем отзывы пациента
        reviewRepository.deleteByPatientId(id);
        
        // Очищаем ссылку на пациента в записях на приём (не удаляем сами слоты)
        appointmentRepository.clearPatientFromAppointments(id);
        
        // Получаем пользователя для удаления
        User user = patient.getUser();
        
        // Удаляем пациента
        patientRepository.deleteById(id);
        
        // Удаляем связанного пользователя, если он существует
        if (user != null) {
            userRepository.deleteById(user.getId());
        }
    }

    private PatientDto mapToDto(Patient patient) {
        UserDto userDto = patient.getUser() != null 
                ? new UserDto(patient.getUser())
                : null;
        
        return new PatientDto(
                patient.getId(),
                userDto,
                patient.getBirthDate(),
                patient.getGender(),
                patient.getInsuranceNumber(),
                patient.getCreatedAt(),
                patient.getUpdatedAt()
        );
    }
}