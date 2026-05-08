package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.Patient;

import java.util.Optional;

/**
 * Репозиторий профилей пациентов.
 * <p>
 * Наследует стандартные операции {@link JpaRepository} и поддерживает поиск пациента по идентификатору связанного пользователя.
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    /**
     * Ищет пациента по идентификатору учётной записи пользователя.
     * <p>
     * JPQL-запрос: выборка {@code Patient}, у которого {@code user.id} равен переданному значению.
     *
     * @param userId идентификатор пользователя
     * @return контейнер с пациентом, если найден; иначе пустой {@link Optional}
     */
    @Query("SELECT p FROM Patient p WHERE p.user.id = :userId")
    Optional<Patient> findByUserId(@Param("userId") Long userId);
}
