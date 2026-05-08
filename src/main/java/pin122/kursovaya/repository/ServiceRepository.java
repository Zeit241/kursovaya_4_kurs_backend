package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pin122.kursovaya.model.Service;

import java.util.Optional;

/**
 * Репозиторий медицинских услуг (справочник услуг клиники).
 * <p>
 * Поддерживает стандартный CRUD и поиск услуги по коду.
 */
public interface ServiceRepository extends JpaRepository<Service, Long> {

    /**
     * Ищет услугу по уникальному коду.
     *
     * @param code код услуги
     * @return контейнер с услугой, если найдена; иначе пустой {@link Optional}
     */
    Optional<Service> findByCode(String code);
}
