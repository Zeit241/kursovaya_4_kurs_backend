package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pin122.kursovaya.model.Role;

import java.util.Optional;

/**
 * Репозиторий ролей пользователей системы.
 * <p>
 * Наследует стандартные операции {@link JpaRepository} и добавляет поиск роли по коду.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Ищет роль по уникальному коду (например, {@code ROLE_PATIENT}).
     *
     * @param code строковый код роли
     * @return контейнер с ролью, если найдена; иначе пустой {@link Optional}
     */
    Optional<Role> findByCode(String code);
}
