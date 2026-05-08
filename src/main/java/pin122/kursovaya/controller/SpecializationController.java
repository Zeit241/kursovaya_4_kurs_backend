package pin122.kursovaya.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.SpecializationDto;
import pin122.kursovaya.model.Specialization;
import pin122.kursovaya.service.SpecializationService;

import java.util.List;

/**
 * REST-контроллер справочника медицинских специализаций.
 * <p>
 * Базовый путь: {@code /api/specializations}.
 *
 * @see SpecializationService
 */
@RestController
@RequestMapping("/api/specializations")
public class SpecializationController {

    private final SpecializationService specializationService;

    /**
     * @param specializationService сервис специализаций
     */
    public SpecializationController(SpecializationService specializationService) {
        this.specializationService = specializationService;
    }

    /**
     * Возвращает все специализации.
     *
     * @return HTTP 200 и список {@link SpecializationDto}
     */
    @GetMapping
    public ResponseEntity<List<SpecializationDto>> getAllSpecializations() {
        return ResponseEntity.ok(specializationService.getAllSpecializations());
    }

    /**
     * Возвращает специализацию по идентификатору.
     *
     * @param id первичный ключ
     * @return HTTP 200 и DTO или HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<SpecializationDto> getSpecializationById(@PathVariable Long id) {
        return specializationService.getSpecializationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Возвращает специализацию по коду.
     *
     * @param code строковый код специализации
     * @return HTTP 200 и DTO или HTTP 404
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<SpecializationDto> getSpecializationByCode(@PathVariable String code) {
        return specializationService.getSpecializationByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Создаёт специализацию.
     *
     * @param specialization сущность {@link Specialization} с валидацией
     * @return HTTP 200 и сохранённая специализация в виде DTO
     */
    @PostMapping
    public ResponseEntity<SpecializationDto> createSpecialization(@Valid @RequestBody Specialization specialization) {
        return ResponseEntity.ok(specializationService.saveSpecialization(specialization));
    }

    /**
     * Обновляет специализацию по идентификатору.
     *
     * @param id              идентификатор записи
     * @param specialization  новые данные
     * @return HTTP 200 и DTO или HTTP 404, если запись не найдена
     */
    @PutMapping("/{id}")
    public ResponseEntity<SpecializationDto> updateSpecialization(
            @PathVariable Long id,
            @Valid @RequestBody Specialization specialization) {
        if (!specializationService.getSpecializationById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        specialization.setId(id);
        return ResponseEntity.ok(specializationService.saveSpecialization(specialization));
    }

    /**
     * Удаляет специализацию по идентификатору.
     *
     * @param id идентификатор специализации
     * @return HTTP 204 при успехе
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSpecialization(@PathVariable Long id) {
        specializationService.deleteSpecialization(id);
        return ResponseEntity.noContent().build();
    }
}

