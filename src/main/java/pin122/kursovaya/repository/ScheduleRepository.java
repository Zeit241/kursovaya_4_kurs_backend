package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pin122.kursovaya.model.Schedule;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findByDoctorId(Long doctorId);
}
