package pin122.kursovaya.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * DTO одной записи приёма в составе отчёта за день: плоская структура с ФИО пациента, врачом, кабинетом и клиническими полями.
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

    private String complaints;
    private String anamnesis;
    private String recommendations;
    
    private OffsetDateTime createdAt;

    public ReportAppointmentDto() {}

    /**
     * @param appointmentId идентификатор приёма
     * @param startTime начало слота
     * @param endTime конец слота
     * @param status статус
     * @param patientId идентификатор пациента
     * @param patientFullName полное имя пациента
     * @param patientPhone телефон пациента
     * @param patientEmail email пациента
     * @param patientBirthDate дата рождения
     * @param patientGender пол
     * @param patientInsuranceNumber номер полиса
     * @param doctorId идентификатор врача
     * @param doctorDisplayName отображаемое имя врача
     * @param doctorPhone телефон врача
     * @param doctorEmail email врача
     * @param roomId идентификатор кабинета
     * @param roomNumber номер или название кабинета
     * @param diagnosis текст или код диагноза для отчёта
     * @param cancelReason причина отмены
     * @param complaints жалобы
     * @param anamnesis анамнез
     * @param recommendations рекомендации
     * @param createdAt время создания записи
     */
    public ReportAppointmentDto(Long appointmentId, OffsetDateTime startTime, OffsetDateTime endTime, String status,
                                Long patientId, String patientFullName, String patientPhone, String patientEmail,
                                LocalDate patientBirthDate, String patientGender, String patientInsuranceNumber,
                                Long doctorId, String doctorDisplayName, String doctorPhone, String doctorEmail,
                                Long roomId, String roomNumber, String diagnosis, String cancelReason,
                                String complaints, String anamnesis, String recommendations,
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
        this.complaints = complaints;
        this.anamnesis = anamnesis;
        this.recommendations = recommendations;
        this.createdAt = createdAt;
    }
}









