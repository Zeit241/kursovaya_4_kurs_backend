package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.QueueEntry;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий записей электронной очереди к врачу.
 * <p>
 * Управляет позициями в очереди, поиском по пациенту и врачу, сдвигом номеров и служебными операциями удаления/подсчёта.
 */
public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    /**
     * Возвращает все записи очереди указанного врача в порядке возрастания позиции.
     *
     * @param doctorId идентификатор врача
     * @return упорядоченный список записей очереди
     */
    List<QueueEntry> findByDoctorIdOrderByPositionAsc(Long doctorId);

    /**
     * Ищет запись очереди для пары «пациент — врач».
     * <p>
     * JPQL-запрос: одна запись {@code QueueEntry} с заданными {@code patient.id} и {@code doctor.id}.
     *
     * @param patientId идентификатор пациента
     * @param doctorId  идентификатор врача
     * @return контейнер с записью, если существует; иначе пустой {@link Optional}
     */
    @Query("SELECT qe FROM QueueEntry qe WHERE qe.patient.id = :patientId AND qe.doctor.id = :doctorId")
    Optional<QueueEntry> findByPatientIdAndDoctorId(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId);

    /**
     * Возвращает все записи очереди для пациента, отсортированные по убыванию времени последнего обновления.
     * <p>
     * JPQL-запрос: фильтр по {@code patient.id}, сортировка {@code lastUpdated DESC}.
     *
     * @param patientId идентификатор пациента
     * @return список записей очереди пациента
     */
    @Query("SELECT qe FROM QueueEntry qe WHERE qe.patient.id = :patientId ORDER BY qe.lastUpdated DESC")
    List<QueueEntry> findByPatientId(@Param("patientId") Long patientId);

    /**
     * Возвращает записи очереди врача с позицией строго меньше заданной, в порядке убывания позиции.
     * <p>
     * JPQL-запрос: {@code doctor.id} и {@code position < :position}, сортировка по позиции по убыванию.
     *
     * @param doctorId идентификатор врача
     * @param position опорная позиция (записи «перед» ней)
     * @return список записей с меньшей позицией
     */
    @Query("SELECT qe FROM QueueEntry qe WHERE qe.doctor.id = :doctorId AND qe.position < :position ORDER BY qe.position DESC")
    List<QueueEntry> findBeforePosition(@Param("doctorId") Long doctorId, @Param("position") Integer position);

    /**
     * Возвращает максимальную позицию в очереди врача или {@code null}, если очередь пуста.
     * <p>
     * JPQL-запрос: {@code MAX(position)} по записям с данным {@code doctor.id}.
     *
     * @param doctorId идентификатор врача
     * @return максимальный номер позиции либо {@code null}
     */
    @Query("SELECT MAX(qe.position) FROM QueueEntry qe WHERE qe.doctor.id = :doctorId")
    Integer findMaxPositionByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Ищет запись очереди врача с точным номером позиции.
     * <p>
     * JPQL-запрос: совпадение {@code doctor.id} и {@code position}.
     *
     * @param doctorId идентификатор врача
     * @param position номер позиции в очереди
     * @return контейнер с записью, если найдена; иначе пустой {@link Optional}
     */
    @Query("SELECT qe FROM QueueEntry qe WHERE qe.doctor.id = :doctorId AND qe.position = :position")
    Optional<QueueEntry> findByDoctorIdAndPosition(@Param("doctorId") Long doctorId, @Param("position") Integer position);

    /**
     * Возвращает записи очереди врача с позицией строго больше заданной, по возрастанию позиции.
     * <p>
     * JPQL-запрос: {@code doctor.id} и {@code position > :position}, сортировка {@code position ASC}.
     *
     * @param doctorId идентификатор врача
     * @param position опорная позиция
     * @return список записей с большей позицией
     */
    @Query("SELECT qe FROM QueueEntry qe WHERE qe.doctor.id = :doctorId AND qe.position > :position ORDER BY qe.position ASC")
    List<QueueEntry> findByDoctorIdAndPositionGreaterThan(@Param("doctorId") Long doctorId, @Param("position") Integer position);

    /**
     * Уменьшает на единицу позицию всех записей очереди врача, у которых позиция больше указанной.
     * <p>
     * JPQL-запрос: массовое {@code UPDATE} поля {@code position} для сдвига после удаления или вставки.
     *
     * @param doctorId идентификатор врача
     * @param position пороговая позиция (записи с {@code position > threshold} сдвигаются)
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE QueueEntry qe SET qe.position = qe.position - 1 WHERE qe.doctor.id = :doctorId AND qe.position > :position")
    void shiftPositionsAfter(@Param("doctorId") Long doctorId, @Param("position") Integer position);

    /**
     * Подсчитывает количество записей очереди для пациента.
     * <p>
     * JPQL-запрос: {@code COUNT} по {@code patient.id}.
     *
     * @param patientId идентификатор пациента
     * @return число записей очереди
     */
    @Query("SELECT COUNT(qe) FROM QueueEntry qe WHERE qe.patient.id = :patientId")
    Long countByPatientId(@Param("patientId") Long patientId);

    /**
     * Удаляет все записи очереди, относящиеся к пациенту.
     * <p>
     * JPQL-запрос: массовое {@code DELETE} по {@code patient.id}.
     *
     * @param patientId идентификатор пациента
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM QueueEntry qe WHERE qe.patient.id = :patientId")
    void deleteByPatientId(@Param("patientId") Long patientId);
}
