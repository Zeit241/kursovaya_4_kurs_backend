package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.Doctor;

import java.util.List;

/**
 * Репозиторий каталога записи: нативные запросы к связке таблиц {@code doctor_specializations}
 * и {@code specialization_services}.
 * <p>
 * В модели JPA нет отдельной сущности для join-таблицы услуг и специализаций, поэтому выборки
 * идут напрямую по SQL для согласования врачей и услуг при бронировании.
 */
public interface BookingCatalogRepository extends Repository<Doctor, Long> {

    /**
     * Возвращает идентификаторы врачей, которые могут оказывать указанную услугу (через общую специализацию).
     * <p>
     * Нативный SQL: пересечение врачей по специализациям и активных привязок услуг к тем же специализациям.
     *
     * @param serviceId идентификатор медицинской услуги
     * @return список уникальных идентификаторов врачей
     */
    @Query(value = """
            SELECT DISTINCT ds.doctor_id
            FROM doctor_specializations ds
            INNER JOIN specialization_services ss ON ss.specialization_id = ds.specialization_id
            WHERE ss.service_id = :serviceId AND ss.is_active = true
            """, nativeQuery = true)
    List<Long> findDoctorIdsByServiceId(@Param("serviceId") Long serviceId);

    /**
     * Возвращает идентификаторы услуг, доступных врачу согласно его специализациям и активным связям в каталоге.
     * <p>
     * Нативный SQL: услуги из {@code specialization_services}, согласованные со специализациями данного врача.
     *
     * @param doctorId идентификатор врача
     * @return список уникальных идентификаторов услуг
     */
    @Query(value = """
            SELECT DISTINCT ss.service_id
            FROM specialization_services ss
            INNER JOIN doctor_specializations ds ON ds.specialization_id = ss.specialization_id
            WHERE ds.doctor_id = :doctorId AND ss.is_active = true
            """, nativeQuery = true)
    List<Long> findServiceIdsByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Возвращает пары «идентификатор услуги — название специализации» для заданного набора услуг.
     * <p>
     * Нативный SQL: соединение {@code specialization_services} со справочником {@code specializations}
     * только для активных связей и переданных {@code serviceIds}, сортировка по идентификатору услуги и имени.
     *
     * @param serviceIds список идентификаторов услуг
     * @return список строк результата: [0] — {@code service_id} ({@link Long}), [1] — наименование специализации ({@link String})
     */
    @Query(value = """
            SELECT ss.service_id, s.name
            FROM specialization_services ss
            INNER JOIN specializations s ON s.id = ss.specialization_id
            WHERE ss.is_active = true AND ss.service_id IN (:serviceIds)
            ORDER BY ss.service_id, s.name
            """, nativeQuery = true)
    List<Object[]> findSpecializationNamesByServiceIds(@Param("serviceIds") List<Long> serviceIds);
}
