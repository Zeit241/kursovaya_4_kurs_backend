package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pin122.kursovaya.model.Specialization;

import java.util.List;
import java.util.Optional;

public interface SpecializationRepository extends JpaRepository<Specialization, Long> {
    Optional<Specialization> findByCode(String code);
    
    /**
     * Получает топ специальностей по количеству врачей
     * @return Список массивов: [Specialization, doctorCount]
     */
    @Query("""
        SELECT s, COUNT(ds.doctor.id) as doctorCount
        FROM Specialization s
        LEFT JOIN DoctorSpecialization ds ON ds.specialization.id = s.id
        GROUP BY s.id
        ORDER BY doctorCount DESC
        """)
    List<Object[]> findTopSpecializationsByDoctorCount();
}

