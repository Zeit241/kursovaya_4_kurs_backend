package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.SpecializationServiceLink;
import pin122.kursovaya.model.SpecializationServiceLinkPk;

import java.util.Collection;
import java.util.List;

/**
 * Репозиторий для сущности связи «специализация — медицинская услуга».
 * <p>
 * Позволяет удалять привязки по идентификатору услуги и выбирать активные связи по набору услуг.
 */
public interface SpecializationServiceLinkRepository extends JpaRepository<SpecializationServiceLink, SpecializationServiceLinkPk> {

    /**
     * Удаляет все связи специализаций с указанной услугой.
     * <p>
     * JPQL-запрос: массовое удаление записей {@code SpecializationServiceLink}, у которых составной ключ содержит заданный {@code serviceId}.
     *
     * @param serviceId идентификатор медицинской услуги, по которому удаляются связи
     */
    @Modifying
    @Query("DELETE FROM SpecializationServiceLink l WHERE l.id.serviceId = :serviceId")
    void deleteByServiceId(@Param("serviceId") Long serviceId);

    /**
     * Возвращает активные связи специализаций с услугами для заданного набора идентификаторов услуг.
     * <p>
     * JPQL-запрос: выборка связей, у которых {@code id.serviceId} входит в переданную коллекцию и флаг {@code active} равен {@code true}.
     *
     * @param serviceIds коллекция идентификаторов услуг
     * @return список активных связей «специализация — услуга»
     */
    @Query("SELECT l FROM SpecializationServiceLink l WHERE l.id.serviceId IN :serviceIds AND l.active = true")
    List<SpecializationServiceLink> findActiveByServiceIds(@Param("serviceIds") Collection<Long> serviceIds);
}
