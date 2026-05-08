package pin122.kursovaya.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pin122.kursovaya.dto.DashboardAppointmentDto;
import pin122.kursovaya.dto.DashboardDto;
import pin122.kursovaya.dto.DoctorDto;
import pin122.kursovaya.dto.SpecializationDto;
import pin122.kursovaya.dto.SpecializationStatsDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.Specialization;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.SpecializationRepository;
import pin122.kursovaya.utils.DoctorPhotoUrls;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Данные главной страницы: топ специализаций, топ врачей по рейтингу, предстоящие приёмы пациента.
 */
@Service
public class DashboardService {

    @Value("${app.directus.public-url:http://localhost:8055}")
    private String directusPublicUrl;

    private final SpecializationRepository specializationRepository;
    private final DoctorService doctorService;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;

    /**
     * @param specializationRepository статистика по врачам на специализацию
     * @param doctorService              выборка и сортировка врачей (рейтинг)
     * @param appointmentRepository      запланированные приёмы пациента
     * @param patientRepository          связь userId → patientId
     */
    public DashboardService(SpecializationRepository specializationRepository,
                           DoctorService doctorService,
                           AppointmentRepository appointmentRepository,
                           PatientRepository patientRepository) {
        this.specializationRepository = specializationRepository;
        this.doctorService = doctorService;
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * Собирает блоки дашборда: топ специализаций, топ-10 врачей по рейтингу, приёмы со статусом {@code scheduled}.
     *
     * @param userId идентификатор пользователя (для списка приёмов); может быть {@code null}
     * @return агрегированный {@link pin122.kursovaya.dto.DashboardDto}
     */
    @Transactional(readOnly = true)
    public DashboardDto getDashboardData(Long userId) {
        // 1. Топ 5 специальностей по количеству врачей
        List<SpecializationStatsDto> topSpecializations = getTopSpecializations(5);
        
        // 2. Топ 10 врачей по рейтингу
        List<DoctorDto> topDoctors = doctorService.getAllDoctors(10, 0, "rating", "desc");
        
        // 3. Запланированные записи для текущего пользователя
        List<DashboardAppointmentDto> upcomingAppointments = getUpcomingAppointments(userId);
        
        return new DashboardDto(topSpecializations, topDoctors, upcomingAppointments);
    }

    /**
     * Возвращает первые {@code limit} специализаций по числу привязанных врачей (как в запросе репозитория).
     *
     * @param limit максимум записей
     * @return список {@link pin122.kursovaya.dto.SpecializationStatsDto}
     */
    private List<SpecializationStatsDto> getTopSpecializations(int limit) {
        List<Object[]> results = specializationRepository.findTopSpecializationsByDoctorCount();
        
        return results.stream()
                .limit(limit)
                .map(row -> {
                    Specialization spec = (Specialization) row[0];
                    Long count = (Long) row[1];
                    return new SpecializationStatsDto(
                            spec.getId(),
                            spec.getCode(),
                            spec.getName(),
                            spec.getDescription(),
                            count
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Находит пациента по {@code userId} и возвращает его приёмы со статусом {@code scheduled}.
     *
     * @param userId идентификатор пользователя
     * @return список {@link pin122.kursovaya.dto.DashboardAppointmentDto}; пустой, если пользователь не пациент
     */
    private List<DashboardAppointmentDto> getUpcomingAppointments(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        
        // Находим пациента по userId
        Optional<Patient> patientOpt = patientRepository.findByUserId(userId);
        if (patientOpt.isEmpty()) {
            return Collections.emptyList();
        }
        
        Long patientId = patientOpt.get().getId();
        
        // Получаем все записи со статусом 'scheduled'
        List<Appointment> appointments = appointmentRepository.findScheduledAppointmentsByPatient(patientId);
        
        return appointments.stream()
                .map(this::mapToDashboardDto)
                .collect(Collectors.toList());
    }

    /**
     * Преобразует {@link Appointment} в DTO карточки приёма для дашборда (врач, кабинет, диагноз).
     *
     * @param appointment сущность приёма
     * @return {@link pin122.kursovaya.dto.DashboardAppointmentDto}
     */
    private DashboardAppointmentDto mapToDashboardDto(Appointment appointment) {
        Doctor doctor = appointment.getDoctor();
        
        // Получаем ФИО доктора из связанного User
        String firstName = null;
        String lastName = null;
        String middleName = null;
        String doctorPhoto = null;
        List<SpecializationDto> doctorSpecializations = Collections.emptyList();
        
        if (doctor != null) {
            if (doctor.getUser() != null) {
                firstName = doctor.getUser().getFirstName();
                lastName = doctor.getUser().getLastName();
                middleName = doctor.getUser().getMiddleName();
            }
            doctorPhoto = DoctorPhotoUrls.toPublicImageUrl(doctor.getPhoto(), directusPublicUrl);
            // Получаем специализации доктора
            if (doctor.getSpecializations() != null) {
                doctorSpecializations = doctor.getSpecializations().stream()
                        .map(ds -> new SpecializationDto(ds.getSpecialization()))
                        .collect(Collectors.toList());
            }
        }
        
        // Получаем название кабинета
        String roomName = appointment.getRoom() != null ? appointment.getRoom().getName() : null;
        
        return new DashboardAppointmentDto(
                appointment.getId(),
                appointment.getSchedule() != null ? appointment.getSchedule().getId() : null,
                doctor != null ? doctor.getId() : null,
                firstName,
                lastName,
                middleName,
                doctorPhoto,
                doctorSpecializations,
                appointment.getPatient() != null ? appointment.getPatient().getId() : null,
                appointment.getRoom() != null ? appointment.getRoom().getId() : null,
                roomName,
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus(),
                appointment.getSource(),
                appointment.getCreatedAt(),
                appointment.getDiagnosis() != null ? appointment.getDiagnosis().getCode() : null
        );
    }
}








 

