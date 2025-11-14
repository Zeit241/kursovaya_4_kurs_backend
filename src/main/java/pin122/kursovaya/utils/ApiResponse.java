package pin122.kursovaya.utils;

import lombok.Data;

import java.util.Optional;

@Data
public class ApiResponse<T> {
    private String message;
    private int status;
    private T data;

    public ApiResponse(String message, int status) {
        this.message = message;
        this.status = status;
    }

    public ApiResponse(String message, int status, T data) {
        this.message = message;
        this.status = status;
        this.data = data;
    }

    // Удобный конструктор с boolean для успеха/ошибки
    public ApiResponse(boolean success, String message, T data) {
        this.message = message;
        this.status = success ? 200 : 400;
        this.data = data;
    }
}