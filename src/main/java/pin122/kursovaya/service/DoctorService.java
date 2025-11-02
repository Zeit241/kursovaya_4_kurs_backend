package pin122.kursovaya.service;

import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import pin122.kursovaya.dto.DoctorDto;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.DoctorRepository;

import java.util.List;
import java.util.Optional;

@Service
public class DoctorService {
    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    public List<DoctorDto> getAllDoctors() {
        return doctorRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    public List<DoctorDto> searchDoctors(String query) {
        List<Doctor> doctors = doctorRepository.searchByFullNameOrSpecialization(query);
        return doctors.stream()
                .map(this::mapToDto)
                .toList();
    }

    public Optional<DoctorDto> getDoctorById(Long id) {
        return doctorRepository.findById(id).stream().map(this::mapToDto).findFirst();
    }

    public DoctorDto saveDoctor(@Valid Doctor doctor) {
        return mapToDto(doctorRepository.save(doctor));
    }

    public void deleteDoctor(Long id) {
        doctorRepository.deleteById(id);
    }

    private DoctorDto mapToDto(Doctor doctor) {
        User user = doctor.getUser();
        UserDto userDto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getMiddleName(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.isActive()
        );

        return new DoctorDto(
                doctor.getId(),
                userDto,
                doctor.getBio(),
                doctor.getExperienceYears(),
                doctor.getPhotoUrl(),
                doctor.getCreatedAt(),
                doctor.getUpdatedAt()
        );
    }
}