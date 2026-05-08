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

/**
 * Управление пациентами: CRUD, каскадное удаление связанных сущностей, создание вместе с пользователем.
 */
@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;
    private final QueueEntryRepository queueEntryRepository;

    /**
     * @param patientRepository      репозиторий пациентов
     * @param userRepository         репозиторий пользователей
     * @param roleRepository         роли (назначение patient)
     * @param appointmentRepository  для очистки ссылок на пациента в приёмах
     * @param reviewRepository       удаление отзывов пациента
     * @param queueEntryRepository   удаление записей очереди
     */
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

    /**
     * Возвращает всех пациентов.
     *
     * @return список {@link PatientDto}
     */
    public List<PatientDto> getAllPatients() {
        return patientRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Ищет пациента по идентификатору.
     *
     * @param id первичный ключ
     * @return {@link PatientDto}, если найден
     */
    public Optional<PatientDto> getPatientById(Long id) {
        return patientRepository.findById(id)
                .map(this::mapToDto);
    }

    /**
     * Сохраняет сущность пациента и возвращает DTO.
     *
     * @param patient сущность для сохранения
     * @return DTO после сохранения
     */
    public PatientDto savePatient(Patient patient) {
        Patient saved = patientRepository.save(patient);
        return mapToDto(saved);
    }

    /**
     * Создаёт пользователя с ролью patient и связанного пациента по данным запроса.
     *
     * @param request валидируемый {@link CreatePatientRequest}
     * @return созданный {@link PatientDto}
     */
    @Transactional
    public PatientDto createPatient(@Valid CreatePatientRequest request) {
        User user = new User();
        user.setEmail(request.getUser().getEmail());
        user.setPhone(FormatUtils.normalizePhone(request.getUser().getPhone()));
        user.setFirstName(request.getUser().getFirstName());
        user.setLastName(request.getUser().getLastName());
        user.setMiddleName(request.getUser().getMiddleName());
        user.setActive(true);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        roleRepository.findByCode("patient").ifPresent(user::setRole);

        User savedUser = userRepository.save(user);

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

    /**
     * Частично обновляет поля пациента и при необходимости связанного {@link User}.
     *
     * @param id             идентификатор пациента
     * @param patientUpdate  сущность с заполненными изменяемыми полями
     * @return обновлённый {@link PatientDto}, если пациент найден
     */
    public Optional<PatientDto> updatePatient(Long id, Patient patientUpdate) {
        return patientRepository.findById(id).map(existingPatient -> {
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

            if (patientUpdate.getUser() != null) {
                User existingUser = existingPatient.getUser();
                if (existingUser != null) {
                    User userUpdate = patientUpdate.getUser();

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
                    existingUser.setActive(userUpdate.isActive());
                    existingUser.setUpdatedAt(java.time.OffsetDateTime.now());

                    userRepository.save(existingUser);
                }
            }

            Patient saved = patientRepository.save(existingPatient);
            return mapToDto(saved);
        });
    }

    /**
     * Удаляет пациента, связанные очереди, отзывы, очищает привязку в приёмах и удаляет {@link User}.
     *
     * @param id идентификатор пациента; если записи нет, метод завершается без ошибки
     */
    @Transactional
    public void deletePatient(Long id) {
        Patient patient = patientRepository.findById(id).orElse(null);
        if (patient == null) {
            return;
        }

        queueEntryRepository.deleteByPatientId(id);

        reviewRepository.deleteByPatientId(id);

        appointmentRepository.clearPatientFromAppointments(id);

        User user = patient.getUser();

        patientRepository.deleteById(id);

        if (user != null) {
            userRepository.deleteById(user.getId());
        }
    }

    /**
     * Собирает {@link PatientDto} с вложенным {@link UserDto}.
     *
     * @param patient сущность из БД
     * @return DTO для API
     */
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
