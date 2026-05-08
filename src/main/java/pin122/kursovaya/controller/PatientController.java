package pin122.kursovaya.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.CreatePatientRequest;
import pin122.kursovaya.dto.PatientDto;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.service.PatientService;
import pin122.kursovaya.utils.ApiResponse;

import java.util.List;

/**
 * REST-контроллер CRUD для пациентов; ответы в обёртке {@link ApiResponse}.
 * <p>
 * Базовый путь: {@code /api/patients}.
 *
 * @see PatientService
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    /**
     * @param patientService сервис пациентов
     */
    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    /**
     * Возвращает список всех пациентов.
     *
     * @return HTTP 200 и {@link ApiResponse} со списком {@link PatientDto}
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PatientDto>>> getAllPatients() {
        List<PatientDto> patients = patientService.getAllPatients();
        return ResponseEntity.ok(new ApiResponse<>(true, "Список пациентов успешно получен", patients));
    }

    /**
     * Возвращает пациента по идентификатору.
     *
     * @param id первичный ключ пациента
     * @return HTTP 200 и данные пациента или HTTP 404 с сообщением об отсутствии записи
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PatientDto>> getPatient(@PathVariable Long id) {
        return patientService.getPatientById(id)
                .map(patient -> ResponseEntity.ok(new ApiResponse<>(true, "Пациент успешно получен", patient)))
                .orElse(ResponseEntity.status(404)
                        .body(new ApiResponse<>(false, "Пациент не найден", null)));
    }

    /**
     * Создаёт пациента по запросу с валидацией.
     *
     * @param request поля нового пациента
     * @return HTTP 200 и созданный {@link PatientDto}
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PatientDto>> createPatient(@Valid @RequestBody CreatePatientRequest request) {
        PatientDto created = patientService.createPatient(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Пациент успешно создан", created));
    }

    /**
     * Обновляет данные пациента.
     *
     * @param id      идентификатор пациента
     * @param patient новые значения полей сущности
     * @return HTTP 200 и обновлённый DTO или HTTP 404, если пациент не найден
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PatientDto>> updatePatient(@PathVariable Long id, @RequestBody Patient patient) {
        return patientService.updatePatient(id, patient)
                .map(updated -> ResponseEntity.ok(new ApiResponse<>(true, "Пациент успешно обновлён", updated)))
                .orElse(ResponseEntity.status(404)
                        .body(new ApiResponse<>(false, "Пациент не найден", null)));
    }

    /**
     * Удаляет пациента по идентификатору.
     *
     * @param id идентификатор пациента
     * @return HTTP 200 с пустым телом в {@link ApiResponse}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePatient(@PathVariable Long id) {
        patientService.deletePatient(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Пациент успешно удалён", null));
    }
}