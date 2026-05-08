package pin122.kursovaya.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * DTO приёма (записи) для REST: идентификаторы, время, статус, медицинские поля и вложенные сведения о пациенте, враче, кабинете, диагнозе и услуге.
 */
@Data
public class AppointmentDto {
    private Long id;
    private Long scheduleId;
    private Long doctorId;
    private Long patientId;
    private Long roomId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Boolean isBooked;
    private String status;
    private String source;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String cancelReason;
    private String complaints;
    private String anamnesis;
    private String recommendations;
    private Long diagnosisId;
    private Long serviceId;
    
    // Вложенные объекты с подробной информацией
    private PatientInfo patient;
    private DoctorInfo doctor;
    private RoomInfo room;
    private DiagnosisInfo diagnosis;
    private ServiceInfo service;

    public AppointmentDto() {}

    /**
     * @param id идентификатор записи
     * @param scheduleId идентификатор расписания
     * @param doctorId идентификатор врача
     * @param patientId идентификатор пациента
     * @param roomId идентификатор кабинета
     * @param startTime начало слота
     * @param endTime конец слота
     * @param isBooked признак занятости слота
     * @param status статус записи
     * @param source источник создания
     * @param createdBy идентификатор пользователя-создателя
     * @param createdAt время создания
     * @param updatedAt время обновления
     * @param cancelReason причина отмены
     * @param complaints жалобы
     * @param anamnesis анамнез
     * @param recommendations рекомендации
     * @param diagnosisId идентификатор диагноза
     * @param serviceId идентификатор услуги
     */
    public AppointmentDto(Long id, Long scheduleId, Long doctorId, Long patientId, Long roomId, 
                         OffsetDateTime startTime, OffsetDateTime endTime, Boolean isBooked, String status, String source, 
                         Long createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt, String cancelReason,
                         String complaints, String anamnesis, String recommendations,
                         Long diagnosisId, Long serviceId) {
        this.id = id;
        this.scheduleId = scheduleId;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.roomId = roomId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isBooked = isBooked;
        this.status = status;
        this.source = source;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.cancelReason = cancelReason;
        this.complaints = complaints;
        this.anamnesis = anamnesis;
        this.recommendations = recommendations;
        this.diagnosisId = diagnosisId;
        this.serviceId = serviceId;
    }
    
    /**
     * Вложенный DTO кратких данных пациента для ответа по приёму.
     */
    @Data
    public static class PatientInfo {
        private Long id;
        private String firstName;
        private String lastName;
        private String middleName;
        private String phone;
        private String email;
        private String birthDate;
        private String gender;
        private String insuranceNumber;
        
        public PatientInfo() {}
    }
    
    /**
     * Вложенный DTO кратких данных врача для ответа по приёму.
     */
    @Data
    public static class DoctorInfo {
        private Long id;
        private String displayName;
        private String firstName;
        private String lastName;
        private String middleName;
        private String specialization;
        private Integer experienceYears;
        /** Публичный URL фото врача. */
        private String photo;
        
        public DoctorInfo() {}
    }
    
    /**
     * Вложенный DTO кабинета для ответа по приёму.
     */
    @Data
    public static class RoomInfo {
        private Long id;
        private String code;
        private String name;
        
        public RoomInfo() {}
    }
    
    /**
     * Вложенный DTO диагноза для ответа по приёму.
     */
    @Data
    public static class DiagnosisInfo {
        private Long id;
        private String code;
        private String name;
        private String category;
        
        public DiagnosisInfo() {}
    }
    
    /**
     * Вложенный DTO услуги для ответа по приёму.
     */
    @Data
    public static class ServiceInfo {
        private Long id;
        private String name;
        private String code;
        private java.math.BigDecimal price;
        private Integer durationMinutes;
        private String description;
        
        public ServiceInfo() {}
    }
}
