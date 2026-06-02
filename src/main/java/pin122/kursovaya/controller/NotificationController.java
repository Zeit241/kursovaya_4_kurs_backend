package pin122.kursovaya.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pin122.kursovaya.dto.SendLoginCredentialsRequest;
import pin122.kursovaya.service.EmailNotificationService;
import pin122.kursovaya.utils.ApiResponse;

/**
 * REST для исходящих уведомлений (email), инициируемых администратором.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final EmailNotificationService emailNotificationService;

    public NotificationController(EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    /**
     * Отправляет письмо с логином и паролем на указанный email.
     */
    @PostMapping("/login-credentials")
    public ResponseEntity<ApiResponse<Void>> sendLoginCredentials(
            @Valid @RequestBody SendLoginCredentialsRequest request) {
        emailNotificationService.sendLoginCredentialsEmail(
                request.getEmail(),
                request.getPassword(),
                request.getRecipientName());
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Письмо с данными для входа отправлено", null));
    }
}
