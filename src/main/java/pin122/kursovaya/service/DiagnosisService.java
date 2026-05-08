package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.DiagnosisDto;
import pin122.kursovaya.model.Diagnosis;
import pin122.kursovaya.repository.DiagnosisRepository;

import java.util.List;
import java.util.Optional;

/**
 * Бизнес-логика справочника диагнозов: чтение, сохранение и удаление через {@link DiagnosisRepository}.
 */
@Service
public class DiagnosisService {

    private final DiagnosisRepository diagnosisRepository;

    /**
     * @param diagnosisRepository репозиторий диагнозов
     */
    public DiagnosisService(DiagnosisRepository diagnosisRepository) {
        this.diagnosisRepository = diagnosisRepository;
    }

    /**
     * Возвращает все диагнозы в виде {@link DiagnosisDto}.
     *
     * @return список DTO, порядок определяется репозиторием
     */
    public List<DiagnosisDto> getAllDiagnoses() {
        return diagnosisRepository.findAll().stream()
                .map(DiagnosisDto::new)
                .toList();
    }

    /**
     * Ищет диагноз по идентификатору.
     *
     * @param id первичный ключ
     * @return {@link DiagnosisDto}, если запись найдена
     */
    public Optional<DiagnosisDto> getDiagnosisById(Long id) {
        return diagnosisRepository.findById(id)
                .map(DiagnosisDto::new);
    }

    /**
     * Ищет диагноз по коду МКБ (или внутреннему коду).
     *
     * @param code код диагноза
     * @return {@link DiagnosisDto}, если запись найдена
     */
    public Optional<DiagnosisDto> getDiagnosisByCode(String code) {
        return diagnosisRepository.findByCode(code)
                .map(DiagnosisDto::new);
    }

    /**
     * Создаёт или обновляет диагноз и возвращает DTO сохранённой сущности.
     *
     * @param diagnosis сущность для сохранения
     * @return DTO после {@link DiagnosisRepository#save(Object)}
     */
    public DiagnosisDto saveDiagnosis(Diagnosis diagnosis) {
        return new DiagnosisDto(diagnosisRepository.save(diagnosis));
    }

    /**
     * Удаляет диагноз по идентификатору.
     *
     * @param id первичный ключ
     */
    public void deleteDiagnosis(Long id) {
        diagnosisRepository.deleteById(id);
    }
}
