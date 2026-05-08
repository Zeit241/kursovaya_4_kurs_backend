package pin122.kursovaya.controller;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.CreateUserDto;
import pin122.kursovaya.dto.CurrentUserDto;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.dto.UserStatsDto;
import pin122.kursovaya.dto.validation.OnCreate;
import pin122.kursovaya.model.User;
import pin122.kursovaya.service.UserService;
import pin122.kursovaya.utils.ApiResponse;

import java.util.List;

/**
 * REST-контроллер пользователей: список, текущий пользователь, статистика, CRUD.
 * <p>
 * Базовый путь: {@code /api/users}. Эндпоинт {@code /api/users/create} объявлен публичным в {@link pin122.kursovaya.security.SecurityConfig}.
 *
 * @see UserService
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    /**
     * @param userService сервис пользователей
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Возвращает список всех пользователей в виде DTO.
     *
     * @return HTTP 200 и список {@link UserDto}
     */
    @GetMapping({"", "/"})
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Возвращает профиль текущего аутентифицированного пользователя с идентификаторами связанных сущностей.
     *
     * @return HTTP 200 и {@link CurrentUserDto} в {@link ApiResponse} или HTTP 404, если пользователь не найден в БД
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserDto>> getCurrentUser() {
        return userService.getCurrentUserWithIds()
                .map(userDto -> ResponseEntity.ok(new ApiResponse<>(true, "Данные пользователя успешно получены", userDto)))
                .orElse(ResponseEntity.status(404)
                        .body(new ApiResponse<>(false, "Пользователь не найден", null)));
    }

    /**
     * Возвращает агрегированную статистику по текущему пользователю.
     *
     * @return HTTP 200 и {@link UserStatsDto} или HTTP 404, если пользователь не найден
     */
    @GetMapping("/userStats")
    public ResponseEntity<ApiResponse<UserStatsDto>> getUserStats() {
        return userService.getUserStats()
                .map(stats -> ResponseEntity.ok(new ApiResponse<>(true, "Статистика пользователя успешно получена", stats)))
                .orElse(ResponseEntity.status(404)
                        .body(new ApiResponse<>(false, "Пользователь не найден", null)));
    }

    /**
     * Возвращает пользователя по идентификатору.
     *
     * @param id первичный ключ пользователя
     * @return HTTP 200 и {@link UserDto} или HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Создаёт пользователя (публичный эндпоинт для административного сценария).
     *
     * @param user   DTO создания пользователя с группой валидации по умолчанию
     * @param result результат валидации полей
     * @return HTTP 200 и {@link UserDto} при успехе, HTTP 400 с {@code null} телом при ошибках валидации, HTTP 404 если создание не удалось
     */
    @PostMapping("/create")
    public ResponseEntity<UserDto> createUser(@Validated @RequestBody CreateUserDto user, BindingResult result) {
        if(result.hasErrors()){
            System.out.println(result.getAllErrors());
            return ResponseEntity.badRequest().body(null);
        }
        return userService.createUser(user).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Обновляет пользователя по идентификатору.
     *
     * @param id          идентификатор пользователя
     * @param userDetails данные для сохранения (валидация с группой {@link OnCreate})
     * @return HTTP 200 и обновлённый {@link UserDto}
     * @throws EntityNotFoundException если пользователь с {@code id} не найден
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @Validated(OnCreate.class) @RequestBody UserDto userDetails) {
        userService.getUserById(id).orElseThrow(()->new EntityNotFoundException("User not found"));
        User savedUser = userService.saveUser(userDetails);
        return ResponseEntity.ok(new UserDto(savedUser));
    }

    /**
     * Удаляет пользователя по идентификатору.
     *
     * @param id идентификатор пользователя
     * @return HTTP 204 при успехе
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}