package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.QueueEntry;

import java.util.List;
import java.util.Optional;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {
    List<QueueEntry> findByDoctorIdOrderByPositionAsc(Long doctorId);
    
    @Query("SELECT qe FROM QueueEntry qe WHERE qe.patient.id = :patientId AND qe.doctor.id = :doctorId")
    Optional<QueueEntry> findByPatientIdAndDoctorId(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId);
    
    @Query("SELECT qe FROM QueueEntry qe WHERE qe.patient.id = :patientId ORDER BY qe.lastUpdated DESC")
    List<QueueEntry> findByPatientId(@Param("patientId") Long patientId);
    
    @Query("SELECT qe FROM QueueEntry qe WHERE qe.doctor.id = :doctorId AND qe.position < :position ORDER BY qe.position DESC")
    List<QueueEntry> findBeforePosition(@Param("doctorId") Long doctorId, @Param("position") Integer position);
    
    @Query("SELECT MAX(qe.position) FROM QueueEntry qe WHERE qe.doctor.id = :doctorId")
    Integer findMaxPositionByDoctorId(@Param("doctorId") Long doctorId);
    
    @Query("SELECT qe FROM QueueEntry qe WHERE qe.doctor.id = :doctorId AND qe.position = :position")
    Optional<QueueEntry> findByDoctorIdAndPosition(@Param("doctorId") Long doctorId, @Param("position") Integer position);
    
    @Query("SELECT qe FROM QueueEntry qe WHERE qe.doctor.id = :doctorId AND qe.position > :position ORDER BY qe.position ASC")
    List<QueueEntry> findByDoctorIdAndPositionGreaterThan(@Param("doctorId") Long doctorId, @Param("position") Integer position);
    
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE QueueEntry qe SET qe.position = qe.position - 1 WHERE qe.doctor.id = :doctorId AND qe.position > :position")
    void shiftPositionsAfter(@Param("doctorId") Long doctorId, @Param("position") Integer position);
    
    @Query("SELECT COUNT(qe) FROM QueueEntry qe WHERE qe.patient.id = :patientId")
    Long countByPatientId(@Param("patientId") Long patientId);
    
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM QueueEntry qe WHERE qe.patient.id = :patientId")
    void deleteByPatientId(@Param("patientId") Long patientId);
}