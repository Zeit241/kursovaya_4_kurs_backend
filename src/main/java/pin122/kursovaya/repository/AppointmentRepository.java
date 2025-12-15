package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByDoctorId(Long doctorId);
    
    /**
     * Находит записи врача с загруженными связанными сущностями
     */
    @Query("SELECT DISTINCT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user " +
           "LEFT JOIN FETCH d.specializations ds " +
           "LEFT JOIN FETCH ds.specialization " +
           "LEFT JOIN FETCH a.room " +
           "WHERE a.doctor.id = :doctorId " +
           "ORDER BY a.startTime DESC")
    List<Appointment> findByDoctorIdWithDetails(@Param("doctorId") Long doctorId);
    List<Appointment> findByPatientId(Long patientId);
    
    /**
     * Находит записи пациента с загруженными связанными сущностями
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user " +
           "LEFT JOIN FETCH d.specializations ds " +
           "LEFT JOIN FETCH ds.specialization " +
           "LEFT JOIN FETCH a.room " +
           "WHERE a.patient.id = :patientId " +
           "ORDER BY a.startTime DESC")
    List<Appointment> findByPatientIdWithDetails(@Param("patientId") Long patientId);
    
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
    
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Appointment a SET a.patient = null WHERE a.patient.id = :patientId")
    void clearPatientFromAppointments(@Param("patientId") Long patientId);
    
    /**
     * Находит все записи пациента со статусом 'scheduled'
     */
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId " +
           "AND a.status = 'scheduled' " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findScheduledAppointmentsByPatient(@Param("patientId") Long patientId);
    
    /**
     * Находит все записи за определённую дату (для отчётов)
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user pu " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user du " +
           "LEFT JOIN FETCH a.room r " +
           "WHERE a.startTime >= :startOfDay AND a.startTime < :startOfNextDay " +
           "AND a.patient IS NOT NULL " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findAllByDate(@Param("startOfDay") OffsetDateTime startOfDay,
                                    @Param("startOfNextDay") OffsetDateTime startOfNextDay);
    
    /**
     * Находит все записи к определённому врачу за определённую дату (для отчётов)
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user pu " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user du " +
           "LEFT JOIN FETCH a.room r " +
           "WHERE a.doctor.id = :doctorId " +
           "AND a.startTime >= :startOfDay AND a.startTime < :startOfNextDay " +
           "AND a.patient IS NOT NULL " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findByDoctorIdAndDateForReport(@Param("doctorId") Long doctorId,
                                                      @Param("startOfDay") OffsetDateTime startOfDay,
                                                      @Param("startOfNextDay") OffsetDateTime startOfNextDay);
    
    /**
     * Находит все записи за период (для отчётов)
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user pu " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user du " +
           "LEFT JOIN FETCH a.room r " +
           "WHERE a.startTime >= :startDate AND a.startTime < :endDate " +
           "AND a.patient IS NOT NULL " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findAllByDateRange(@Param("startDate") OffsetDateTime startDate,
                                          @Param("endDate") OffsetDateTime endDate);
    
    /**
     * Находит все записи к определённому врачу за период (для отчётов)
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user pu " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user du " +
           "LEFT JOIN FETCH a.room r " +
           "WHERE a.doctor.id = :doctorId " +
           "AND a.startTime >= :startDate AND a.startTime < :endDate " +
           "AND a.patient IS NOT NULL " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findByDoctorIdAndDateRangeForReport(@Param("doctorId") Long doctorId,
                                                           @Param("startDate") OffsetDateTime startDate,
                                                           @Param("endDate") OffsetDateTime endDate);
    
    /**
     * Подсчитывает количество записей по статусам за период
     */
    @Query("SELECT a.status, COUNT(a) FROM Appointment a " +
           "WHERE a.startTime >= :startDate AND a.startTime < :endDate " +
           "AND a.patient IS NOT NULL " +
           "GROUP BY a.status")
    List<Object[]> countByStatusAndDateRange(@Param("startDate") OffsetDateTime startDate,
                                              @Param("endDate") OffsetDateTime endDate);
    
    /**
     * Подсчитывает количество записей по статусам для врача за период
     */
    @Query("SELECT a.status, COUNT(a) FROM Appointment a " +
           "WHERE a.doctor.id = :doctorId " +
           "AND a.startTime >= :startDate AND a.startTime < :endDate " +
           "AND a.patient IS NOT NULL " +
           "GROUP BY a.status")
    List<Object[]> countByStatusAndDoctorAndDateRange(@Param("doctorId") Long doctorId,
                                                       @Param("startDate") OffsetDateTime startDate,
                                                       @Param("endDate") OffsetDateTime endDate);
    
    /**
     * Находит все запланированные приёмы в заданном временном диапазоне (для напоминаний)
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user pu " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.specializations ds " +
           "LEFT JOIN FETCH ds.specialization s " +
           "LEFT JOIN FETCH a.room r " +
           "WHERE a.startTime >= :startTime AND a.startTime < :endTime " +
           "AND a.status IN ('scheduled', 'confirmed') " +
           "AND a.patient IS NOT NULL " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findScheduledAppointmentsBetween(@Param("startTime") OffsetDateTime startTime,
                                                        @Param("endTime") OffsetDateTime endTime);
    
    /**
     * Находит все appointments в заданном временном диапазоне (для очереди на день)
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH a.doctor d " +
           "WHERE a.startTime >= :startTime AND a.startTime < :endTime " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findByStartTimeBetween(@Param("startTime") OffsetDateTime startTime,
                                              @Param("endTime") OffsetDateTime endTime);
    
    List<Appointment> findByStatus(String status);
    
    /**
     * Находит запись с загруженными пациентом, пользователем и врачом (для уведомлений)
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user " +
           "LEFT JOIN FETCH d.specializations ds " +
           "LEFT JOIN FETCH ds.specialization " +
           "LEFT JOIN FETCH a.room " +
           "WHERE a.id = :id")
    Optional<Appointment> findByIdWithDetails(@Param("id") Long id);
    
    @Query("SELECT a FROM Appointment a WHERE a.status = :status AND a.doctor.id = :doctorId ORDER BY a.startTime ASC")
    List<Appointment> findByStatusAndDoctorId(@Param("status") String status, @Param("doctorId") Long doctorId);
    
    @Query("SELECT a FROM Appointment a WHERE a.status = :status " +
           "AND a.startTime >= :startOfDay AND a.startTime < :startOfNextDay ORDER BY a.startTime ASC")
    List<Appointment> findByStatusAndDate(@Param("status") String status,
                                          @Param("startOfDay") OffsetDateTime startOfDay,
                                          @Param("startOfNextDay") OffsetDateTime startOfNextDay);
    
    @Query("SELECT a FROM Appointment a WHERE a.status = :status AND a.doctor.id = :doctorId " +
           "AND a.startTime >= :startOfDay AND a.startTime < :startOfNextDay ORDER BY a.startTime ASC")
    List<Appointment> findByStatusAndDoctorIdAndDate(@Param("status") String status,
                                                      @Param("doctorId") Long doctorId,
                                                      @Param("startOfDay") OffsetDateTime startOfDay,
                                                      @Param("startOfNextDay") OffsetDateTime startOfNextDay);
    
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.startTime >= :startOfDay AND a.startTime < :startOfNextDay ORDER BY a.startTime ASC")
    List<Appointment> findByDoctorIdAndDateRange(@Param("doctorId") Long doctorId,
                                                  @Param("startOfDay") OffsetDateTime startOfDay,
                                                  @Param("startOfNextDay") OffsetDateTime startOfNextDay);
    
    @Query("SELECT a FROM Appointment a WHERE a.startTime >= :startOfDay AND a.startTime < :startOfNextDay ORDER BY a.startTime ASC")
    List<Appointment> findByDateRange(@Param("startOfDay") OffsetDateTime startOfDay,
                                      @Param("startOfNextDay") OffsetDateTime startOfNextDay);
}