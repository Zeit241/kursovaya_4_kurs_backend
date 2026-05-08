package pin122.kursovaya.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.model.Room;
import pin122.kursovaya.repository.RoomRepository;

import java.util.List;
import java.util.Map;

/**
 * REST-контроллер кабинетов (помещений приёма): список, создание, обновление, удаление.
 * <p>
 * Базовый путь: {@code /api/rooms}.
 *
 * @see RoomRepository
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepository;

    /**
     * @param roomRepository репозиторий сущностей {@link Room}
     */
    public RoomController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * Возвращает все кабинеты из БД.
     *
     * @return HTTP 200 и список {@link Room}
     */
    @GetMapping
    public ResponseEntity<List<Room>> getAll() {
        return ResponseEntity.ok(roomRepository.findAll());
    }

    /**
     * Возвращает кабинет по идентификатору.
     *
     * @param id первичный ключ кабинета
     * @return HTTP 200 и сущность или HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<Room> getById(@PathVariable Long id) {
        return roomRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Создаёт кабинет с обязательным непустым уникальным кодом.
     *
     * @param room данные кабинета (код обязателен)
     * @return HTTP 200 и сохранённая сущность или HTTP 400 с телом {@code { "error": "..." }}
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Room room) {
        if (room.getCode() == null || room.getCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Код кабинета обязателен"));
        }
        
        // Проверяем, не существует ли кабинет с таким кодом
        if (roomRepository.findByCode(room.getCode().trim()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Кабинет с таким кодом уже существует"));
        }
        
        room.setCode(room.getCode().trim());
        if (room.getName() != null) {
            room.setName(room.getName().trim());
        }
        
        Room saved = roomRepository.save(room);
        return ResponseEntity.ok(saved);
    }

    /**
     * Обновляет код и/или название кабинета; код не должен совпадать с другим кабинетом.
     *
     * @param id   идентификатор кабинета
     * @param room поля для обновления
     * @return HTTP 200 и сохранённая сущность, HTTP 404 если кабинет не найден
     * @apiNote При занятом коде другим кабинетом выбрасывается {@link IllegalArgumentException} из лямбды обновления.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Room room) {
        return roomRepository.findById(id)
                .map(existing -> {
                    if (room.getCode() != null && !room.getCode().isBlank()) {
                        // Проверяем, не занят ли код другим кабинетом
                        roomRepository.findByCode(room.getCode().trim())
                                .filter(r -> !r.getId().equals(id))
                                .ifPresent(r -> {
                                    throw new IllegalArgumentException("Кабинет с таким кодом уже существует");
                                });
                        existing.setCode(room.getCode().trim());
                    }
                    if (room.getName() != null) {
                        existing.setName(room.getName().trim());
                    }
                    return ResponseEntity.ok(roomRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Удаляет кабинет по идентификатору.
     *
     * @param id идентификатор кабинета
     * @return HTTP 204 при успехе или HTTP 404, если записи нет
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!roomRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        roomRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}







