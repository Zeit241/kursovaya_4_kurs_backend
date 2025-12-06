package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = {"roles"})
    @Query("SELECT u FROM User u WHERE u.email = :email")
    User findByEmail(@Param("email") String email);
    
    @EntityGraph(attributePaths = {"roles", "patient", "doctor", "doctor.specializations", "doctor.specializations.specialization"})
    @Query("SELECT u FROM User u WHERE u.email = :email")
    User findByEmailWithPatientAndDoctor(@Param("email") String email);
    
    Optional<User> findByEmailOrPhone(String email, String phone);
}
