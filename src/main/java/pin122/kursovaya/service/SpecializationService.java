package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.SpecializationDto;
import pin122.kursovaya.model.Specialization;
import pin122.kursovaya.repository.SpecializationRepository;

import java.util.List;
import java.util.Optional;

/**
 * Бизнес-логика справочника медицинских специализаций.
 */
@Service
public class SpecializationService {

    private final SpecializationRepository specializationRepository;

    /**
     * @param specializationRepository репозиторий специализаций
     */
    public SpecializationService(SpecializationRepository specializationRepository) {
        this.specializationRepository = specializationRepository;
    }

    /**
     * Возвращает все специализации.
     *
     * @return список {@link SpecializationDto}
     */
    public List<SpecializationDto> getAllSpecializations() {
        return specializationRepository.findAll().stream()
                .map(SpecializationDto::new)
                .toList();
    }

    /**
     * Ищет специализацию по идентификатору.
     *
     * @param id первичный ключ
     * @return {@link SpecializationDto}, если найдена
     */
    public Optional<SpecializationDto> getSpecializationById(Long id) {
        return specializationRepository.findById(id)
                .map(SpecializationDto::new);
    }

    /**
     * Ищет специализацию по коду.
     *
     * @param code уникальный код специализации
     * @return {@link SpecializationDto}, если найдена
     */
    public Optional<SpecializationDto> getSpecializationByCode(String code) {
        return specializationRepository.findByCode(code)
                .map(SpecializationDto::new);
    }

    /**
     * Сохраняет специализацию и возвращает DTO.
     *
     * @param specialization сущность для сохранения
     * @return DTO сохранённой записи
     */
    public SpecializationDto saveSpecialization(Specialization specialization) {
        return new SpecializationDto(specializationRepository.save(specialization));
    }

    /**
     * Удаляет специализацию по идентификатору.
     *
     * @param id первичный ключ
     */
    public void deleteSpecialization(Long id) {
        specializationRepository.deleteById(id);
    }
}
