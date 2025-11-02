package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.QueueEntry;

import java.util.List;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {
    List<QueueEntry> findByDoctorIdOrderByPositionAsc(Long doctorId);
}