package pin122.kursovaya.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.ServiceDto;
import pin122.kursovaya.dto.SetServiceSpecializationsRequest;
import pin122.kursovaya.service.ServiceService;

import java.util.List;

/**
 * REST-контроллер медицинских услуг и привязки услуг к специализациям.
 * <p>
 * Базовый путь: {@code /api/services}.
 *
 * @see ServiceService
 */
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceService serviceService;

    /**
     * @param serviceService сервис услуг
     */
    public ServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    /**
     * Возвращает все услуги или только те, что доступны для указанного врача.
     *
     * @param doctorId опциональный идентификатор врача для фильтрации
     * @return HTTP 200 и список {@link ServiceDto}
     */
    @GetMapping
    public ResponseEntity<List<ServiceDto>> getAllServices(
            @RequestParam(name = "doctorId", required = false) Long doctorId) {
        if (doctorId != null) {
            return ResponseEntity.ok(serviceService.getServicesForDoctor(doctorId));
        }
        return ResponseEntity.ok(serviceService.getAllServices());
    }

    /**
     * Возвращает услугу по идентификатору.
     *
     * @param id первичный ключ услуги
     * @return HTTP 200 и DTO или HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<ServiceDto> getServiceById(@PathVariable Long id) {
        return serviceService.getServiceById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Возвращает услугу по коду.
     *
     * @param code строковый код услуги
     * @return HTTP 200 и DTO или HTTP 404
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<ServiceDto> getServiceByCode(@PathVariable String code) {
        return serviceService.getServiceByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Создаёт услугу.
     *
     * @param service сущность {@link pin122.kursovaya.model.Service} с валидацией
     * @return HTTP 200 и сохранённая услуга в виде DTO
     */
    @PostMapping
    public ResponseEntity<ServiceDto> createService(@Valid @RequestBody pin122.kursovaya.model.Service service) {
        return ResponseEntity.ok(serviceService.saveService(service));
    }

    /**
     * Обновляет услугу по идентификатору.
     *
     * @param id      идентификатор услуги
     * @param service новые данные
     * @return HTTP 200 и DTO или HTTP 404, если услуга не найдена
     */
    @PutMapping("/{id}")
    public ResponseEntity<ServiceDto> updateService(
            @PathVariable Long id,
            @Valid @RequestBody pin122.kursovaya.model.Service service) {
        if (!serviceService.getServiceById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        service.setId(id);
        return ResponseEntity.ok(serviceService.saveService(service));
    }

    /**
     * Задаёт набор специализаций для услуги (полная замена списка связей).
     *
     * @param id   идентификатор услуги
     * @param body запрос с {@code specializationIds}; {@code null} или пустой список снимает все привязки
     * @return HTTP 200 и обновлённый {@link ServiceDto} или HTTP 404
     */
    @PutMapping("/{id}/specializations")
    public ResponseEntity<ServiceDto> setServiceSpecializations(
            @PathVariable Long id,
            @RequestBody(required = false) SetServiceSpecializationsRequest body) {
        List<Long> ids = body != null && body.getSpecializationIds() != null
                ? body.getSpecializationIds()
                : List.of();
        return serviceService.setServiceSpecializations(id, ids)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Удаляет услугу по идентификатору.
     *
     * @param id идентификатор услуги
     * @return HTTP 204 при успехе
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        serviceService.deleteService(id);
        return ResponseEntity.noContent().build();
    }
}
