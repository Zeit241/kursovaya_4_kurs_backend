package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pin122.kursovaya.model.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);
}



