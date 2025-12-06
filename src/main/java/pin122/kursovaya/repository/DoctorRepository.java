package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.Doctor;

import java.util.List;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @EntityGraph(attributePaths = {"specializations", "specializations.specialization"})
    @Query("""
        SELECT DISTINCT d FROM Doctor d
        JOIN d.user u
        LEFT JOIN d.specializations ds
        LEFT JOIN ds.specialization s
        WHERE 
            LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(u.middleName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.code) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    List<Doctor> searchByFullNameOrSpecialization(@Param("query") String query);
    
    @Override
    @EntityGraph(attributePaths = {"specializations", "specializations.specialization"})
    java.util.Optional<Doctor> findById(Long id);
    
    @EntityGraph(attributePaths = {"specializations", "specializations.specialization"})
    java.util.List<Doctor> findAll();
    
    @EntityGraph(attributePaths = {"specializations", "specializations.specialization"})
    java.util.List<Doctor> findAll(org.springframework.data.domain.Sort sort);
    
    @EntityGraph(attributePaths = {"specializations", "specializations.specialization"})
    org.springframework.data.domain.Page<Doctor> findAll(org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT d FROM Doctor d WHERE d.user.id = :userId")
    java.util.Optional<Doctor> findByUserId(@Param("userId") Long userId);
}