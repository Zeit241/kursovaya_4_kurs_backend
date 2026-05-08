package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.Review;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий отзывов о врачах.
 * <p>
 * Поддерживает выборку по врачу и приёму, агрегаты по рейтингу и количеству, а также удаление отзывов пациента.
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Возвращает все отзывы о указанном враче с подгрузкой пациента и пользователя пациента.
     * <p>
     * JPQL-запрос: отзывы, у которых {@code doctor.id} равен {@code :doctorId}.
     *
     * @param doctorId идентификатор врача
     * @return список отзывов
     */
    @EntityGraph(attributePaths = {"patient", "patient.user"})
    @Query("SELECT r FROM Review r WHERE r.doctor.id = :doctorId")
    List<Review> findByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Возвращает отзыв по идентификатору с подгрузкой пациента и пользователя пациента.
     *
     * @param id идентификатор отзыва
     * @return контейнер с отзывом, если найден; иначе пустой {@link Optional}
     */
    @Override
    @EntityGraph(attributePaths = {"patient", "patient.user"})
    Optional<Review> findById(Long id);

    /**
     * Ищет отзыв, привязанный к указанному приёму (записи).
     * <p>
     * JPQL-запрос: {@code appointment.id} равен {@code :appointmentId}.
     *
     * @param appointmentId идентификатор приёма
     * @return контейнер с отзывом, если существует; иначе пустой {@link Optional}
     */
    @EntityGraph(attributePaths = {"patient", "patient.user"})
    @Query("SELECT r FROM Review r WHERE r.appointment.id = :appointmentId")
    Optional<Review> findByAppointmentId(@Param("appointmentId") Long appointmentId);

    /**
     * Вычисляет средний рейтинг врача по всем отзывам.
     * <p>
     * JPQL-запрос: {@code AVG(r.rating)} для отзывов с данным {@code doctor.id}.
     *
     * @param doctorId идентификатор врача
     * @return среднее значение рейтинга в контейнере, либо пустой {@link Optional}, если отзывов нет
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.doctor.id = :doctorId")
    Optional<Double> findAverageRatingByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Подсчитывает число отзывов о враче.
     * <p>
     * JPQL-запрос: {@code COUNT} по {@code doctor.id}.
     *
     * @param doctorId идентификатор врача
     * @return количество отзывов
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.doctor.id = :doctorId")
    Long countByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Подсчитывает число отзывов, оставленных пациентом.
     * <p>
     * JPQL-запрос: {@code COUNT} по {@code patient.id}.
     *
     * @param patientId идентификатор пациента
     * @return количество отзывов пациента
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.patient.id = :patientId")
    Long countByPatientId(@Param("patientId") Long patientId);

    /**
     * Удаляет все отзывы указанного пациента.
     * <p>
     * JPQL-запрос: массовое {@code DELETE} по {@code patient.id}.
     *
     * @param patientId идентификатор пациента
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Review r WHERE r.patient.id = :patientId")
    void deleteByPatientId(@Param("patientId") Long patientId);
}
