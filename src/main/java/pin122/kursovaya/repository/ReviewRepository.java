package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.Review;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    @EntityGraph(attributePaths = {"patient", "patient.user"})
    @Query("SELECT r FROM Review r WHERE r.doctor.id = :doctorId")
    List<Review> findByDoctorId(@Param("doctorId") Long doctorId);
    
    @Override
    @EntityGraph(attributePaths = {"patient", "patient.user"})
    Optional<Review> findById(Long id);
    
    @EntityGraph(attributePaths = {"patient", "patient.user"})
    @Query("SELECT r FROM Review r WHERE r.appointment.id = :appointmentId")
    Optional<Review> findByAppointmentId(@Param("appointmentId") Long appointmentId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.doctor.id = :doctorId")
    Optional<Double> findAverageRatingByDoctorId(@Param("doctorId") Long doctorId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.doctor.id = :doctorId")
    Long countByDoctorId(@Param("doctorId") Long doctorId);
    
    @Query("SELECT COUNT(r) FROM Review r WHERE r.patient.id = :patientId")
    Long countByPatientId(@Param("patientId") Long patientId);
    
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Review r WHERE r.patient.id = :patientId")
    void deleteByPatientId(@Param("patientId") Long patientId);
}