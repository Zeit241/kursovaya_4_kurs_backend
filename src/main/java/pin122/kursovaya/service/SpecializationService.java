package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.SpecializationDto;
import pin122.kursovaya.model.Specialization;
import pin122.kursovaya.repository.SpecializationRepository;

import java.util.List;
import java.util.Optional;

@Service
public class SpecializationService {

    private final SpecializationRepository specializationRepository;

    public SpecializationService(SpecializationRepository specializationRepository) {
        this.specializationRepository = specializationRepository;
    }

    public List<SpecializationDto> getAllSpecializations() {
        return specializationRepository.findAll().stream()
                .map(SpecializationDto::new)
                .toList();
    }

    public Optional<SpecializationDto> getSpecializationById(Long id) {
        return specializationRepository.findById(id)
                .map(SpecializationDto::new);
    }

    public Optional<SpecializationDto> getSpecializationByCode(String code) {
        return specializationRepository.findByCode(code)
                .map(SpecializationDto::new);
    }

    public SpecializationDto saveSpecialization(Specialization specialization) {
        return new SpecializationDto(specializationRepository.save(specialization));
    }

    public void deleteSpecialization(Long id) {
        specializationRepository.deleteById(id);
    }
}

