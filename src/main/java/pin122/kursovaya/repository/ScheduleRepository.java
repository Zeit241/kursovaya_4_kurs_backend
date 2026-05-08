package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pin122.kursovaya.model.Schedule;

import java.time.LocalDate;
import java.util.List;

/**
 * Репозиторий расписаний врачей (график работы по датам).
 * <p>
 * Позволяет получать все записи расписания врача или записи на конкретную календарную дату.
 */
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /**
     * Возвращает все элементы расписания, привязанные к указанному врачу.
     *
     * @param doctorId идентификатор врача
     * @return список записей расписания
     */
    List<Schedule> findByDoctorId(Long doctorId);

    /**
     * Возвращает записи расписания врача на заданную дату.
     *
     * @param doctorId идентификатор врача
     * @param dateAt    календарная дата
     * @return список записей расписания на эту дату
     */
    List<Schedule> findByDoctorIdAndDateAt(Long doctorId, LocalDate dateAt);
}
