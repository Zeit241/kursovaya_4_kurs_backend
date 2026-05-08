package pin122.kursovaya.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Тело REST-запроса на создание расписания врача: врач, кабинет (существующий или новый), дата, интервал и длительность слота.
 */
@Data
public class CreateScheduleRequest {
    private DoctorIdDto doctor;
    private RoomIdDto room;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateAt;
    
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;
    
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;
    
    private Integer slotDurationMinutes;

    /**
     * Вложенный объект с идентификатором врача в запросе создания расписания.
     */
    @Data
    public static class DoctorIdDto {
        private Long id;
    }

    /**
     * Вложенный объект кабинета: либо {@code id} существующего кабинета, либо {@code code}/{@code name} для создания нового.
     */
    @Data
    public static class RoomIdDto {
        private Long id;
        // Поля для создания нового кабинета, если id не указан
        private String code;
        private String name;
    }
}

