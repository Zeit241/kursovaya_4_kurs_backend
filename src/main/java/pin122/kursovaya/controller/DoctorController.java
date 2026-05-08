package pin122.kursovaya.controller;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.CreateDoctorRequest;
import pin122.kursovaya.dto.DoctorDto;
import pin122.kursovaya.dto.UpdateDoctorRequest;
import pin122.kursovaya.service.DoctorService;
import pin122.kursovaya.utils.ApiResponse;

import java.util.List;

/**
 * REST-контроллер врачей: список с поиском и фильтром по услуге, CRUD.
 * <p>
 * Базовый путь: {@code /api/doctors}.
 *
 * @see DoctorService
 */
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    /**
     * @param doctorService сервис врачей
     */
    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    /**
     * Возвращает список врачей с опциональным поиском, пагинацией и сортировкой; при {@code serviceId} — только врачи услуги.
     *
     * @param q         строка поиска (опционально)
     * @param serviceId идентификатор услуги для фильтра (опционально)
     * @param limit     ограничение числа записей
     * @param offset    смещение выборки
     * @param sortBy    поле сортировки
     * @param sortOrder порядок сортировки ({@code asc} по умолчанию)
     * @return HTTP 200 и {@link ApiResponse} со списком {@link DoctorDto}
     */
    @GetMapping({"/", ""})
    public ResponseEntity<ApiResponse<List<DoctorDto>>> getAllDoctors(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "serviceId", required = false) Long serviceId,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "sortBy", required = false) String sortBy,
            @RequestParam(name = "sortOrder", required = false, defaultValue = "asc") String sortOrder) {
        List<DoctorDto> doctors;
        if (serviceId != null) {
            doctors = doctorService.listDoctorsForService(serviceId, q, limit, offset, sortBy, sortOrder);
        } else if (q == null || q.trim().isEmpty()) {
            doctors = doctorService.getAllDoctors(limit, offset, sortBy, sortOrder);
        } else {
            doctors = doctorService.searchDoctors(q.trim(), limit, offset, sortBy, sortOrder);
        }

        return ResponseEntity.ok(new ApiResponse<>(true, "Список врачей успешно получен", doctors));
    }

    /**
     * Возвращает врача по идентификатору.
     *
     * @param id идентификатор врача
     * @return HTTP 200 и данные врача или HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorDto>> getDoctorById(@PathVariable Long id) {
        return doctorService.getDoctorById(id)
                .map(doctor -> ResponseEntity.ok(new ApiResponse<>(true, "Врач успешно получен", doctor)))
                .orElse(ResponseEntity.status(404)
                        .body(new ApiResponse<>(false, "Врач не найден", null)));
    }

    /**
     * Создаёт карточку врача по запросу с валидацией.
     *
     * @param request поля нового врача
     * @return HTTP 200 и созданный {@link DoctorDto}
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<DoctorDto>> createDoctor(@Valid @RequestBody CreateDoctorRequest request) {
        DoctorDto created = doctorService.createDoctor(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Врач успешно создан", created));
    }

    /**
     * Обновляет данные врача.
     *
     * @param id      идентификатор врача
     * @param request новые значения полей
     * @return HTTP 200 и обновлённый DTO или HTTP 404
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorDto>> updateDoctor(@PathVariable Long id, @Valid @RequestBody UpdateDoctorRequest request) {
        return doctorService.updateDoctor(id, request)
                .map(updated -> ResponseEntity.ok(new ApiResponse<>(true, "Врач успешно обновлён", updated)))
                .orElse(ResponseEntity.status(404)
                        .body(new ApiResponse<>(false, "Врач не найден", null)));
    }

    /**
     * Удаляет врача по идентификатору.
     *
     * @param id идентификатор врача
     * @return HTTP 200 с сообщением об успехе в {@link ApiResponse}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDoctor(@PathVariable Long id) {
        doctorService.deleteDoctor(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Врач успешно удалён", null));
    }
}