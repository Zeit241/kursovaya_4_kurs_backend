package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pin122.kursovaya.model.Specialization;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий медицинских специализаций.
 * <p>
 * Обеспечивает поиск по коду, подсчёт существующих записей по списку идентификаторов и агрегированную выборку «топа» по числу врачей.
 */
public interface SpecializationRepository extends JpaRepository<Specialization, Long> {

    /**
     * Ищет специализацию по уникальному строковому коду.
     *
     * @param code код специализации
     * @return контейнер со специализацией, если найдена; иначе пустой {@link Optional}
     */
    Optional<Specialization> findByCode(String code);

    /**
     * Подсчитывает количество специализаций, чьи идентификаторы входят в переданную коллекцию.
     *
     * @param ids коллекция идентификаторов специализаций
     * @return число найденных специализаций
     */
    long countByIdIn(Collection<Long> ids);

    /**
     * Возвращает список специализаций, отсортированный по убыванию количества привязанных врачей.
     * <p>
     * JPQL-запрос: для каждой специализации подсчитывается число записей в связи {@code DoctorSpecialization} (через {@code LEFT JOIN}),
     * результат группируется по идентификатору специализации и упорядочивается по убыванию счётчика.
     *
     * @return список массивов объектов: элемент [0] — сущность специализации, элемент [1] — число врачей ({@code Long})
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
