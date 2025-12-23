package pin122.kursovaya.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * DTO для детальной информации о записи в отчёте
 */
@Data
public class ReportAppointmentDto {
    private Long appointmentId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String status;
    
    // Информация о пациенте
    private Long patientId;
    private String patientFullName;
    private String patientPhone;
    private String patientEmail;
    private LocalDate patientBirthDate;
    private String patientGender;
    private String patientInsuranceNumber;
    
    // Информация о враче
    private Long doctorId;
    private String doctorDisplayName;
    private String doctorPhone;
    private String doctorEmail;
    
    // Информация о кабинете
    private Long roomId;
    private String roomNumber;
    
    // Диагноз и причина отмены
    private String diagnosis;
    private String cancelReason;
    
    private OffsetDateTime createdAt;

    public ReportAppointmentDto() {}

    public ReportAppointmentDto(Long appointmentId, OffsetDateTime startTime, OffsetDateTime endTime, String status,
                                Long patientId, String patientFullName, String patientPhone, String patientEmail,
                                LocalDate patientBirthDate, String patientGender, String patientInsuranceNumber,
                                Long doctorId, String doctorDisplayName, String doctorPhone, String doctorEmail,
                                Long roomId, String roomNumber, String diagnosis, String cancelReason,
                                OffsetDateTime createdAt) {
        this.appointmentId = appointmentId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.patientId = patientId;
        this.patientFullName = patientFullName;
        this.patientPhone = patientPhone;
        this.patientEmail = patientEmail;
        this.patientBirthDate = patientBirthDate;
        this.patientGender = patientGender;
        this.patientInsuranceNumber = patientInsuranceNumber;
        this.doctorId = doctorId;
        this.doctorDisplayName = doctorDisplayName;
        this.doctorPhone = doctorPhone;
        this.doctorEmail = doctorEmail;
        this.roomId = roomId;
        this.roomNumber = roomNumber;
        this.diagnosis = diagnosis;
        this.cancelReason = cancelReason;
        this.createdAt = createdAt;
    }
}






