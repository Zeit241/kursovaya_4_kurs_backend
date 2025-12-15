package pin122.kursovaya.dto;

import lombok.Data;

import java.time.OffsetDateTime;

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
    private String diagnosis;
    
    // Вложенные объекты с подробной информацией
    private PatientInfo patient;
    private DoctorInfo doctor;
    private RoomInfo room;

    public AppointmentDto() {}

    public AppointmentDto(Long id, Long scheduleId, Long doctorId, Long patientId, Long roomId, 
                         OffsetDateTime startTime, OffsetDateTime endTime, Boolean isBooked, String status, String source, 
                         Long createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt, String cancelReason, String diagnosis) {
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
        this.diagnosis = diagnosis;
    }
    
    // Вложенный класс для информации о пациенте
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
    
    // Вложенный класс для информации о враче
    @Data
    public static class DoctorInfo {
        private Long id;
        private String displayName;
        private String firstName;
        private String lastName;
        private String middleName;
        private String specialization;
        private Integer experienceYears;
        private String photo;
        
        public DoctorInfo() {}
    }
    
    // Вложенный класс для информации о кабинете
    @Data
    public static class RoomInfo {
        private Long id;
        private String code;
        private String name;
        
        public RoomInfo() {}
    }
}
