package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pin122.kursovaya.model.Review;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByDoctorId(Long doctorId);
}