package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;

import java.util.Date;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByDoctorId(Long doctorId);
    List<Appointment> findByPatientId(Long patientId);

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
}