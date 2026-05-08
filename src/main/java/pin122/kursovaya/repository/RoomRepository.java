package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pin122.kursovaya.model.Room;

import java.util.Optional;

/**
 * Репозиторий кабинетов (помещений для приёма).
 * <p>
 * Обеспечивает стандартный доступ к сущности {@link Room} и поиск кабинета по коду.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * Ищет кабинет по уникальному коду.
     *
     * @param code код кабинета
     * @return контейнер с кабинетом, если найден; иначе пустой {@link Optional}
     */
    Optional<Room> findByCode(String code);
}
