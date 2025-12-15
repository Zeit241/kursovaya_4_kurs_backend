package pin122.kursovaya.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.model.Room;
import pin122.kursovaya.repository.RoomRepository;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepository;

    public RoomController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * Получить все кабинеты
     */
    @GetMapping
    public ResponseEntity<List<Room>> getAll() {
        return ResponseEntity.ok(roomRepository.findAll());
    }

    /**
     * Получить кабинет по ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Room> getById(@PathVariable Long id) {
        return roomRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Создать новый кабинет
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
     * Обновить кабинет
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
     * Удалить кабинет
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

