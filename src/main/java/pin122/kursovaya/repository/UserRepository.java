package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.User;

import java.util.Optional;

/**
 * Репозиторий пользователей системы.
 * <p>
 * Предоставляет поиск по электронной почте с разной глубиной подгрузки графа сущностей (роль, пациент, врач)
 * и проверку уникальности по email или телефону.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Находит пользователя по адресу электронной почты с подгрузкой роли.
     * <p>
     * JPQL-запрос: равенство {@code email}; {@link EntityGraph} подгружает связь {@code role}.
     *
     * @param email адрес электронной почты
     * @return найденный пользователь или {@code null}, если пользователь не существует
     */
    @EntityGraph(attributePaths = {"role"})
    @Query("SELECT u FROM User u WHERE u.email = :email")
    User findByEmail(@Param("email") String email);

    /**
     * Находит пользователя по email с расширенным графом: роль, пациент, врач и специализации врача.
     * <p>
     * JPQL-запрос: равенство {@code email}; граф включает {@code role}, {@code patient}, {@code doctor}
     * и вложенные {@code doctor.specializations} с {@code specialization}.
     *
     * @param email адрес электронной почты
     * @return найденный пользователь или {@code null}, если пользователь не существует
     */
    @EntityGraph(attributePaths = {"role", "patient", "doctor", "doctor.specializations", "doctor.specializations.specialization"})
    @Query("SELECT u FROM User u WHERE u.email = :email")
    User findByEmailWithPatientAndDoctor(@Param("email") String email);

    /**
     * Ищет пользователя по email или номеру телефона (метод Spring Data по соглашению об имени).
     *
     * @param email строка email для сравнения
     * @param phone строка телефона для сравнения
     * @return контейнер с пользователем, если найден совпадающий email или телефон; иначе пустой {@link Optional}
     */
    Optional<User> findByEmailOrPhone(String email, String phone);
}
