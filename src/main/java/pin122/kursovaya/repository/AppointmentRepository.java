package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByDoctorId(Long doctorId);
    List<Appointment> findByPatientId(Long patientId);
    
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.startTime >= :startOfDay AND a.startTime < :startOfNextDay ORDER BY a.startTime")
    List<Appointment> findByDoctorIdAndDate(@Param("doctorId") Long doctorId, 
                                             @Param("startOfDay") OffsetDateTime startOfDay,
                                             @Param("startOfNextDay") OffsetDateTime startOfNextDay);
    
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId " +
           "AND a.patient IS NOT NULL " +
           "AND a.startTime >= :currentTime " +
           "AND a.status NOT IN ('completed', 'cancelled') " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findUpcomingAppointmentsByPatient(@Param("patientId") Long patientId,
                                                         @Param("currentTime") OffsetDateTime currentTime);
    
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId " +
           "AND a.doctor.id = :doctorId " +
           "AND a.patient IS NOT NULL " +
           "AND a.startTime >= :currentTime " +
           "AND a.status NOT IN ('completed', 'cancelled') " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findUpcomingAppointmentsByPatientAndDoctor(@Param("patientId") Long patientId,
                                                                   @Param("doctorId") Long doctorId,
                                                                   @Param("currentTime") OffsetDateTime currentTime);

    @Query("""
        SELECT d FROM Doctor d
        JOIN d.user u
        LEFT JOIN DoctorSpecialization ds ON d.id = ds.doctor.id
        LEFT JOIN Specialization s ON ds.specialization.id = s.id
        WHERE 
            LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(u.middleName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.code) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    List<Doctor> checkAvailability(Date start, Date end, Long doctorId);
    
    /**
     * Находит все приёмы со статусом 'scheduled' или 'confirmed', у которых время окончания прошло
     * @param currentTime Текущее время
     * @return Список просроченных приёмов
     */
    @Query("SELECT a FROM Appointment a WHERE a.status IN ('scheduled', 'confirmed') " +
           "AND a.endTime < :currentTime AND a.patient IS NOT NULL")
    List<Appointment> findExpiredAppointments(@Param("currentTime") OffsetDateTime currentTime);
    
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.patient.id = :patientId")
    Long countByPatientId(@Param("patientId") Long patientId);
}