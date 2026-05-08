package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pin122.kursovaya.model.Notification;

import java.util.List;

/**
 * Репозиторий уведомлений пользователей.
 * <p>
 * Поддерживает выборку всех уведомлений пользователя и фильтрацию по статусу (прочитано/непрочитано и т.д.).
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Возвращает все уведомления, относящиеся к указанному пользователю.
     *
     * @param userId идентификатор пользователя
     * @return список уведомлений
     */
    List<Notification> findByUserId(Long userId);

    /**
     * Возвращает уведомления пользователя с заданным статусом.
     *
     * @param userId идентификатор пользователя
     * @param status строковый статус уведомления
     * @return отфильтрованный список уведомлений
     */
    List<Notification> findByUserIdAndStatus(Long userId, String status);
}
