package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.PatientDto;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    public PatientService(PatientRepository patientRepository, UserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
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
                existingPatient.setInsuranceNumber(patientUpdate.getInsuranceNumber());
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
                        existingUser.setPhone(userUpdate.getPhone());
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

    public void deletePatient(Long id) {
        patientRepository.deleteById(id);
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