package pin122.kursovaya.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO предстоящего приёма для главной панели: врач с ФИО и специализациями, кабинет, время, статус, диагноз.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardAppointmentDto {
    private Long id;
    private Long scheduleId;
    private Long doctorId;
    private String doctorFirstName;              // Имя врача
    private String doctorLastName;               // Фамилия врача
    private String doctorMiddleName;             // Отчество врача
    /** Публичный URL фото врача для UI. */
    private String doctorPhoto;
    private List<SpecializationDto> doctorSpecializations; // Специализации доктора
    private Long patientId;
    private Long roomId;
    private String roomName;                     // Название кабинета
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String status;
    private String source;
    private OffsetDateTime createdAt;
    private String diagnosis;
}









