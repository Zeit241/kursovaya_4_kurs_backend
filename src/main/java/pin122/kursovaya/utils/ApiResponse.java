package pin122.kursovaya.utils;

import lombok.Data;

import java.util.Optional;

/**
 * Унифицированная обёртка ответа REST API: признак успеха, HTTP-подобный статус, сообщение и полезная нагрузка.
 *
 * @param <T> тип данных в поле {@code data}
 */
@Data
public class ApiResponse<T> {
    private Boolean success;
    private String message;
    private Integer status;
    private T data;

    /**
     * Создаёт ответ только с сообщением и числовым статусом; {@code success} выводится из диапазона 2xx.
     *
     * @param message текст сообщения для клиента
     * @param status  код состояния (например, HTTP)
     */
    public ApiResponse(String message, int status) {
        this.message = message;
        this.status = status;
        this.success = status >= 200 && status < 300;
    }

    /**
     * Создаёт ответ с сообщением, статусом и данными; {@code success} выводится из диапазона 2xx.
     *
     * @param message текст сообщения для клиента
     * @param status  код состояния (например, HTTP)
     * @param data    полезная нагрузка ответа
     */
    public ApiResponse(String message, int status, T data) {
        this.message = message;
        this.status = status;
        this.data = data;
        this.success = status >= 200 && status < 300;
    }

    /**
     * Создаёт ответ с явным признаком успеха; при неуспехе статус устанавливается в 400, при успехе — 200.
     *
     * @param success признак успешного выполнения операции
     * @param message текст сообщения для клиента
     * @param data    полезная нагрузка ответа
     */
    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.status = success ? 200 : 400;
        this.data = data;
    }
}
