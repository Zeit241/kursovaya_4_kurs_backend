package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.DailyReportDto;
import pin122.kursovaya.dto.ReportAppointmentDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.DoctorRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для формирования сводных отчётов
 */
@Service
public class ReportService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    public ReportService(AppointmentRepository appointmentRepository, DoctorRepository doctorRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
    }

    /**
     * Получить перечень всех записанных пациентов на определённую дату
     */
    public DailyReportDto getAllAppointmentsByDate(LocalDate date) {
        OffsetDateTime startOfDay = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime startOfNextDay = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Appointment> appointments = appointmentRepository.findAllByDate(startOfDay, startOfNextDay);
        List<ReportAppointmentDto> appointmentDtos = appointments.stream()
                .map(this::mapToReportDto)
                .collect(Collectors.toList());

        // Подсчёт статистики
        int scheduledCount = 0;
        int completedCount = 0;
        int cancelledCount = 0;
        int noShowCount = 0;

        for (Appointment a : appointments) {
            switch (a.getStatus()) {
                case "scheduled":
                case "confirmed":
                    scheduledCount++;
                    break;
                case "completed":
                    completedCount++;
                    break;
                case "cancelled":
                    cancelledCount++;
                    break;
                case "no_show":
                    noShowCount++;
                    break;
            }
        }

        return new DailyReportDto(
                date,
                appointments.size(),
                scheduledCount,
                completedCount,
                cancelledCount,
                noShowCount,
                null,
                null,
                appointmentDtos
        );
    }

    /**
     * Получить перечень записанных пациентов на определённую дату к определённому врачу
     */
    public DailyReportDto getAppointmentsByDoctorAndDate(Long doctorId, LocalDate date) {
        OffsetDateTime startOfDay = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime startOfNextDay = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Appointment> appointments = appointmentRepository.findByDoctorIdAndDateForReport(
                doctorId, startOfDay, startOfNextDay);
        
        List<ReportAppointmentDto> appointmentDtos = appointments.stream()
                .map(this::mapToReportDto)
                .collect(Collectors.toList());

        // Получаем информацию о враче
        String doctorDisplayName = null;
        Optional<Doctor> doctorOpt = doctorRepository.findById(doctorId);
        if (doctorOpt.isPresent()) {
            doctorDisplayName = doctorOpt.get().getDisplayName();
        }

        // Подсчёт статистики
        int scheduledCount = 0;
        int completedCount = 0;
        int cancelledCount = 0;
        int noShowCount = 0;

        for (Appointment a : appointments) {
            switch (a.getStatus()) {
                case "scheduled":
                case "confirmed":
                    scheduledCount++;
                    break;
                case "completed":
                    completedCount++;
                    break;
                case "cancelled":
                    cancelledCount++;
                    break;
                case "no_show":
                    noShowCount++;
                    break;
            }
        }

        return new DailyReportDto(
                date,
                appointments.size(),
                scheduledCount,
                completedCount,
                cancelledCount,
                noShowCount,
                doctorId,
                doctorDisplayName,
                appointmentDtos
        );
    }

    /**
     * Получить перечень записей за период
     */
    public DailyReportDto getAppointmentsByDateRange(LocalDate startDate, LocalDate endDate) {
        OffsetDateTime start = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Appointment> appointments = appointmentRepository.findAllByDateRange(start, end);
        List<ReportAppointmentDto> appointmentDtos = appointments.stream()
                .map(this::mapToReportDto)
                .collect(Collectors.toList());

        // Подсчёт статистики
        int scheduledCount = 0;
        int completedCount = 0;
        int cancelledCount = 0;
        int noShowCount = 0;

        for (Appointment a : appointments) {
            switch (a.getStatus()) {
                case "scheduled":
                case "confirmed":
                    scheduledCount++;
                    break;
                case "completed":
                    completedCount++;
                    break;
                case "cancelled":
                    cancelledCount++;
                    break;
                case "no_show":
                    noShowCount++;
                    break;
            }
        }

        return new DailyReportDto(
                startDate,
                appointments.size(),
                scheduledCount,
                completedCount,
                cancelledCount,
                noShowCount,
                null,
                null,
                appointmentDtos
        );
    }

    /**
     * Получить перечень записей к врачу за период
     */
    public DailyReportDto getAppointmentsByDoctorAndDateRange(Long doctorId, LocalDate startDate, LocalDate endDate) {
        OffsetDateTime start = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Appointment> appointments = appointmentRepository.findByDoctorIdAndDateRangeForReport(
                doctorId, start, end);
        
        List<ReportAppointmentDto> appointmentDtos = appointments.stream()
                .map(this::mapToReportDto)
                .collect(Collectors.toList());

        // Получаем информацию о враче
        String doctorDisplayName = null;
        Optional<Doctor> doctorOpt = doctorRepository.findById(doctorId);
        if (doctorOpt.isPresent()) {
            doctorDisplayName = doctorOpt.get().getDisplayName();
        }

        // Подсчёт статистики
        int scheduledCount = 0;
        int completedCount = 0;
        int cancelledCount = 0;
        int noShowCount = 0;

        for (Appointment a : appointments) {
            switch (a.getStatus()) {
                case "scheduled":
                case "confirmed":
                    scheduledCount++;
                    break;
                case "completed":
                    completedCount++;
                    break;
                case "cancelled":
                    cancelledCount++;
                    break;
                case "no_show":
                    noShowCount++;
                    break;
            }
        }

        return new DailyReportDto(
                startDate,
                appointments.size(),
                scheduledCount,
                completedCount,
                cancelledCount,
                noShowCount,
                doctorId,
                doctorDisplayName,
                appointmentDtos
        );
    }

    /**
     * Преобразование Appointment в ReportAppointmentDto
     */
    private ReportAppointmentDto mapToReportDto(Appointment appointment) {
        ReportAppointmentDto dto = new ReportAppointmentDto();
        
        dto.setAppointmentId(appointment.getId());
        dto.setStartTime(appointment.getStartTime());
        dto.setEndTime(appointment.getEndTime());
        dto.setStatus(appointment.getStatus());
        dto.setDiagnosis(appointment.getDiagnosis());
        dto.setCancelReason(appointment.getCancelReason());
        dto.setCreatedAt(appointment.getCreatedAt());

        // Информация о пациенте
        Patient patient = appointment.getPatient();
        if (patient != null) {
            dto.setPatientId(patient.getId());
            dto.setPatientBirthDate(patient.getBirthDate());
            dto.setPatientInsuranceNumber(patient.getInsuranceNumber());
            
            if (patient.getGender() != null) {
                dto.setPatientGender(patient.getGender() == 1 ? "Мужской" : "Женский");
            }

            User patientUser = patient.getUser();
            if (patientUser != null) {
                dto.setPatientFullName(buildFullName(patientUser));
                dto.setPatientPhone(patientUser.getPhone());
                dto.setPatientEmail(patientUser.getEmail());
            }
        }

        // Информация о враче
        Doctor doctor = appointment.getDoctor();
        if (doctor != null) {
            dto.setDoctorId(doctor.getId());
            dto.setDoctorDisplayName(doctor.getDisplayName());

            User doctorUser = doctor.getUser();
            if (doctorUser != null) {
                dto.setDoctorPhone(doctorUser.getPhone());
                dto.setDoctorEmail(doctorUser.getEmail());
            }
        }

        // Информация о кабинете
        if (appointment.getRoom() != null) {
            dto.setRoomId(appointment.getRoom().getId());
            dto.setRoomNumber(appointment.getRoom().getCode());
        }

        return dto;
    }

    /**
     * Формирование полного имени из User
     */
    private String buildFullName(User user) {
        StringBuilder sb = new StringBuilder();
        
        if (user.getLastName() != null && !user.getLastName().isEmpty()) {
            sb.append(user.getLastName());
        }
        
        if (user.getFirstName() != null && !user.getFirstName().isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(user.getFirstName());
        }
        
        if (user.getMiddleName() != null && !user.getMiddleName().isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(user.getMiddleName());
        }
        
        return sb.toString();
    }
}



