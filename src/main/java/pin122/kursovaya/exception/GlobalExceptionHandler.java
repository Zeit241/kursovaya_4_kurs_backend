package pin122.kursovaya.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import pin122.kursovaya.utils.ApiResponse;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();

        String userMessage = "Ошибка целостности данных.";

        // Примитивный разбор некоторых типичных ошибок
        if (message != null && message.contains("Key (email)=")) {
            userMessage = "Пользователь с таким email уже существует.";
        } else if (message != null && message.contains("Key (phone)=")) {
            userMessage = "Пользователь с таким телефоном уже существует.";
        } else if (message != null && message.toLowerCase().contains("unique")) {
            userMessage = "Нарушено уникальное ограничение в базе данных.";
        }

        Map<String, Object> details = new HashMap<>();
        details.put("rawMessage", message);
        details.put("code", "DATA_INTEGRITY_VIOLATION");

        ApiResponse<Map<String, Object>> response =
                new ApiResponse<>(false, userMessage, details);

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                fieldErrors.put(err.getField(), err.getDefaultMessage())
        );

        Map<String, Object> details = new HashMap<>();
        details.put("code", "VALIDATION_ERROR");
        details.put("errors", fieldErrors);

        ApiResponse<Map<String, Object>> response =
                new ApiResponse<>(false, "Данные не прошли валидацию.", details);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOtherExceptions(Exception ex) {
        logger.error("=== GlobalExceptionHandler поймал исключение ===");
        logger.error("Тип: {}", ex.getClass().getName());
        logger.error("Сообщение: {}", ex.getMessage());
        logger.error("Stack trace:", ex);
        
        ApiResponse<Object> response =
                new ApiResponse<>(false, "Внутренняя ошибка сервера.", null);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}


