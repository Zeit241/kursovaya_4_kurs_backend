package pin122.kursovaya.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.DiagnosisDto;
import pin122.kursovaya.model.Diagnosis;
import pin122.kursovaya.service.DiagnosisService;

import java.util.List;

/**
 * REST-контроллер справочника диагнозов (МКБ и связанные поля).
 * <p>
 * Базовый путь: {@code /api/diagnoses}.
 *
 * @see DiagnosisService
 */
@RestController
@RequestMapping("/api/diagnoses")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    /**
     * @param diagnosisService бизнес-логика диагнозов
     */
    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    /**
     * Возвращает все диагнозы в виде DTO.
     *
     * @return HTTP 200 и список {@link DiagnosisDto}
     */
    @GetMapping
    public ResponseEntity<List<DiagnosisDto>> getAllDiagnoses() {
        return ResponseEntity.ok(diagnosisService.getAllDiagnoses());
    }

    /**
     * Возвращает диагноз по числовому идентификатору.
     *
     * @param id первичный ключ диагноза
     * @return HTTP 200 и DTO или HTTP 404, если не найден
     */
    @GetMapping("/{id}")
    public ResponseEntity<DiagnosisDto> getDiagnosisById(@PathVariable Long id) {
        return diagnosisService.getDiagnosisById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Возвращает диагноз по коду (например, коду МКБ).
     *
     * @param code строковый код диагноза
     * @return HTTP 200 и DTO или HTTP 404, если не найден
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<DiagnosisDto> getDiagnosisByCode(@PathVariable String code) {
        return diagnosisService.getDiagnosisByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Создаёт новый диагноз.
     *
     * @param diagnosis тело сущности {@link Diagnosis} с валидируемыми полями
     * @return HTTP 200 и сохранённый диагноз в виде DTO
     */
    @PostMapping
    public ResponseEntity<DiagnosisDto> createDiagnosis(@Valid @RequestBody Diagnosis diagnosis) {
        return ResponseEntity.ok(diagnosisService.saveDiagnosis(diagnosis));
    }

    /**
     * Обновляет существующий диагноз по идентификатору.
     *
     * @param id        идентификатор обновляемой записи
     * @param diagnosis новые данные диагноза
     * @return HTTP 200 и DTO или HTTP 404, если запись с {@code id} не существует
     */
    @PutMapping("/{id}")
    public ResponseEntity<DiagnosisDto> updateDiagnosis(
            @PathVariable Long id,
            @Valid @RequestBody Diagnosis diagnosis) {
        if (!diagnosisService.getDiagnosisById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        diagnosis.setId(id);
        return ResponseEntity.ok(diagnosisService.saveDiagnosis(diagnosis));
    }

    /**
     * Удаляет диагноз по идентификатору.
     *
     * @param id идентификатор диагноза
     * @return HTTP 204 при успехе
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDiagnosis(@PathVariable Long id) {
        diagnosisService.deleteDiagnosis(id);
        return ResponseEntity.noContent().build();
    }
}
