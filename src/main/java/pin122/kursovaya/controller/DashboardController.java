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

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;

    public DashboardController(DashboardService dashboardService, UserRepository userRepository) {
        this.dashboardService = dashboardService;
        this.userRepository = userRepository;
    }

    /**
     * Получает данные для дашборда:
     * 1. Топ 5 специальностей по количеству врачей
     * 2. Топ 10 врачей по рейтингу
     * 3. Запланированные записи для текущего пользователя
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




