package pin122.kursovaya.utils;

import lombok.Data;

import java.util.Optional;

@Data
public class ApiResponse<T> {
    private Boolean success;
    private String message;
    private Integer status;
    private T data;

    public ApiResponse(String message, int status) {
        this.message = message;
        this.status = status;
        this.success = status >= 200 && status < 300;
    }

    public ApiResponse(String message, int status, T data) {
        this.message = message;
        this.status = status;
        this.data = data;
        this.success = status >= 200 && status < 300;
    }

    // Удобный конструктор с boolean для успеха/ошибки
    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.status = success ? 200 : 400;
        this.data = data;
    }
}