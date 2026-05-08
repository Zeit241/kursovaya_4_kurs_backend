package pin122.kursovaya.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.CreateScheduleRequest;
import pin122.kursovaya.dto.ScheduleDto;
import pin122.kursovaya.service.ScheduleService;

import java.time.LocalDate;
import java.util.List;

/**
 * REST-контроллер расписаний врачей (слотов/дней работы).
 * <p>
 * Базовый путь: {@code /api/schedules}.
 *
 * @see ScheduleService
 */
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    /**
     * @param scheduleService сервис расписаний
     */
    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /**
     * Возвращает все расписания.
     *
     * @return HTTP 200 и список {@link ScheduleDto}
     */
    @GetMapping
    public ResponseEntity<List<ScheduleDto>> getAll() {
        return ResponseEntity.ok(scheduleService.getAllSchedules());
    }

    /**
     * Возвращает расписания врача, опционально отфильтрованные по дате.
     *
     * @param doctorId идентификатор врача
     * @param date     дата в формате ISO (опционально)
     * @return HTTP 200 и список DTO
     */
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<ScheduleDto>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(scheduleService.getSchedulesByDoctor(doctorId, date));
    }

    /**
     * Возвращает расписание по идентификатору.
     *
     * @param id первичный ключ расписания
     * @return HTTP 200 и DTO или HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<ScheduleDto> getById(@PathVariable Long id) {
        return scheduleService.getScheduleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Создаёт расписание; требуются врач с {@code id} и дата {@code dateAt}.
     *
     * @param request DTO создания расписания
     * @return HTTP 200 и созданное расписание или HTTP 400 при отсутствии врача или даты
     */
    @PostMapping
    public ResponseEntity<ScheduleDto> create(@RequestBody CreateScheduleRequest request) {
        if (request.getDoctor() == null || request.getDoctor().getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getDateAt() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(scheduleService.createSchedule(request));
    }

    /**
     * Удаляет расписание по идентификатору.
     *
     * @param id идентификатор расписания
     * @return HTTP 204 при успехе
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleService.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }
}