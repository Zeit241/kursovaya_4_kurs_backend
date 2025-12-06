package pin122.kursovaya.dto;

import lombok.Data;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class CurrentUserDto {
    private Long id;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String middleName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private boolean active;
    private Long patientId;
    private Long doctorId;
    private PatientInfo patient;
    private DoctorInfo doctor;

    public CurrentUserDto() {
    }

    public CurrentUserDto(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.phone = user.getPhone();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.middleName = user.getMiddleName();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
        this.active = user.isActive();
        
        Patient patient = user.getPatient();
        if (patient != null) {
            this.patientId = patient.getId();
            this.patient = new PatientInfo(patient);
        } else {
            this.patientId = null;
            this.patient = null;
        }
        
        Doctor doctor = user.getDoctor();
        if (doctor != null) {
            this.doctorId = doctor.getId();
            this.doctor = new DoctorInfo(doctor);
        } else {
            this.doctorId = null;
            this.doctor = null;
        }
    }

    @Data
    public static class PatientInfo {
        private Long id;
        private LocalDate birthDate;
        private Short gender;
        private String insuranceNumber;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public PatientInfo() {
        }

        public PatientInfo(Patient patient) {
            this.id = patient.getId();
            this.birthDate = patient.getBirthDate();
            this.gender = patient.getGender();
            this.insuranceNumber = patient.getInsuranceNumber();
            this.createdAt = patient.getCreatedAt();
            this.updatedAt = patient.getUpdatedAt();
        }
    }

    @Data
    public static class DoctorInfo {
        private Long id;
        private String displayName;
        private String bio;
        private Integer experienceYears;
        private String photo; // Base64-кодированное изображение
        private List<SpecializationDto> specializations;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public DoctorInfo() {
        }

        public DoctorInfo(Doctor doctor) {
            this.id = doctor.getId();
            this.displayName = doctor.getDisplayName();
            this.bio = doctor.getBio();
            this.experienceYears = doctor.getExperienceYears();
            
            // Конвертируем фото в Base64 строку
            if (doctor.getPhoto() != null && doctor.getPhoto().length > 0) {
                this.photo = Base64.getEncoder().encodeToString(doctor.getPhoto());
            } else {
                this.photo = null;
            }
            
            this.createdAt = doctor.getCreatedAt();
            this.updatedAt = doctor.getUpdatedAt();
            
            // Загружаем специализации
            if (doctor.getSpecializations() != null) {
                this.specializations = doctor.getSpecializations().stream()
                        .map(ds -> new SpecializationDto(ds.getSpecialization()))
                        .collect(Collectors.toList());
            } else {
                this.specializations = List.of();
            }
        }
    }
}


