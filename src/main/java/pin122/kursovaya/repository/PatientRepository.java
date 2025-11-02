package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pin122.kursovaya.model.Patient;

public interface PatientRepository extends JpaRepository<Patient, Long> {
}