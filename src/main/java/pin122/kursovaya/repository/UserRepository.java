package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pin122.kursovaya.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    Optional<User> findByEmailOrPhone(String email, String phone);
}
