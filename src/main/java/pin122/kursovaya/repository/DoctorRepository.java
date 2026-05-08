package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.Doctor;

import java.util.Collection;
import java.util.List;

/**
 * Репозиторий врачей.
 * <p>
 * Поддерживает пакетную выборку с детализацией (специализации, пользователь), полнотекстоподобный поиск
 * по ФИО и данным специализаций, а также поиск врача по идентификатору пользователя.
 */
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    /**
     * Загружает врачей по списку идентификаторов с жадной подгрузкой специализаций и пользователя.
     * <p>
     * JPQL-запрос: {@code Doctor} с {@code id IN :ids}; {@link EntityGraph} добавляет пути
     * {@code specializations}, {@code specializations.specialization}, {@code user}.
     *
     * @param ids коллекция идентификаторов врачей
     * @return список врачей с заполненными указанными связями
     */
    @EntityGraph(attributePaths = {"specializations", "specializations.specialization", "user"})
    @Query("SELECT d FROM Doctor d WHERE d.id IN :ids")
    List<Doctor> findAllByIdInWithDetails(@Param("ids") Collection<Long> ids);

    /**
     * Ищет врачей по подстроке в имени, отчестве, фамилии пользователя или в названии/коде специализации (без учёта регистра).
     * <p>
     * JPQL-запрос: соединение врача с пользователем и левые соединения со специализациями; условие — {@code LIKE}
     * по полям ФИО и специализации с параметром {@code :query}.
     *
     * @param query подстрока поиска
     * @return список подходящих врачей (без дубликатов по сущности)
     */
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

    /**
     * Возвращает врача по идентификатору с подгрузкой специализаций.
     * <p>
     * Переопределение {@link JpaRepository#findById(Object)} с {@link EntityGraph} для связей
     * {@code specializations} и {@code specializations.specialization}.
     *
     * @param id идентификатор врача
     * @return контейнер с врачом, если найден; иначе пустой {@link java.util.Optional}
     */
    @Override
    @EntityGraph(attributePaths = {"specializations", "specializations.specialization"})
    java.util.Optional<Doctor> findById(Long id);

    /**
     * Возвращает всех врачей с подгрузкой специализаций.
     *
     * @return список всех врачей с заполненными связями специализаций
     */
    @EntityGraph(attributePaths = {"specializations", "specializations.specialization"})
    java.util.List<Doctor> findAll();

    /**
     * Возвращает всех врачей с подгрузкой специализаций в заданном порядке сортировки.
     *
     * @param sort параметры сортировки Spring Data
     * @return отсортированный список врачей
     */
    @EntityGraph(attributePaths = {"specializations", "specializations.specialization"})
    java.util.List<Doctor> findAll(org.springframework.data.domain.Sort sort);

    /**
     * Возвращает страницу врачей с подгрузкой специализаций.
     *
     * @param pageable параметры постраничной выборки и сортировки
     * @return страница врачей
     */
    @EntityGraph(attributePaths = {"specializations", "specializations.specialization"})
    org.springframework.data.domain.Page<Doctor> findAll(org.springframework.data.domain.Pageable pageable);

    /**
     * Ищет врача по идентификатору связанной учётной записи пользователя.
     * <p>
     * JPQL-запрос: {@code Doctor}, у которого {@code user.id} равен {@code :userId}.
     *
     * @param userId идентификатор пользователя
     * @return контейнер с врачом, если найден; иначе пустой {@link java.util.Optional}
     */
    @Query("SELECT d FROM Doctor d WHERE d.user.id = :userId")
    java.util.Optional<Doctor> findByUserId(@Param("userId") Long userId);
}
