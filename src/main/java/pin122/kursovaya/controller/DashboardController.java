package pin122.kursovaya.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pin122.kursovaya.dto.DashboardDto;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.service.DashboardService;

/**
 * REST-контроллер данных главной страницы (дашборда): топ специальностей и врачей, персональные записи.
 * <p>
 * Базовый путь: {@code /api/dashboard}.
 *
 * @see DashboardService
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;

    /**
     * @param dashboardService сервис агрегации данных дашборда
     * @param userRepository   поиск пользователя по email из {@link Authentication}
     */
    public DashboardController(DashboardService dashboardService, UserRepository userRepository) {
        this.dashboardService = dashboardService;
        this.userRepository = userRepository;
    }

    /**
     * Возвращает агрегированные данные дашборда: топ специальностей по числу врачей, топ врачей по рейтингу,
     * запланированные приёмы для текущего пользователя (если передан {@link Authentication}).
     *
     * @param authentication контекст Spring Security или {@code null} для гостя
     * @return HTTP 200 и {@link DashboardDto}
     */
    @GetMapping
    public ResponseEntity<DashboardDto> getDashboard(Authentication authentication) {
        Long userId = null;
        
        // Получаем userId из аутентификации, если пользователь авторизован
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            User user = userRepository.findByEmail(email);
            if (user != null) {
                userId = user.getId();
            }
        }
        
        DashboardDto dashboard = dashboardService.getDashboardData(userId);
        return ResponseEntity.ok(dashboard);
    }
}










