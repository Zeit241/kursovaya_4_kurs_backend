package pin122.kursovaya.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.repository.AppointmentRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Зарезервированная фоновая задача для проверки просроченных приёмов.
 *
 * <p>Планировка отключена; ранее предполагалось переводить статусы в {@code no_show}. Сейчас {@link #checkExpiredAppointments()} не выполняет действий.
 */
@Component
public class AppointmentExpirationTask {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentExpirationTask.class);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;

    /**
     * @param appointmentRepository репозиторий приёмов
     * @param appointmentService    сервис приёмов
     */
    public AppointmentExpirationTask(AppointmentRepository appointmentRepository,
                                     AppointmentService appointmentService) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
    }

    /**
     * Ранее: приёмы со статусом {@code scheduled}/{@code confirmed} с {@code endTime} в прошлом переводились в {@code no_show}.
     *
     * <p>Сейчас проверки отключены; аннотация {@link Scheduled} закомментирована.
     */
    // @Scheduled(fixedRate = 60_000) // каждую минуту
    public void checkExpiredAppointments() {
        // Проверки времени отключены - статус приема не меняется автоматически
        return;
    }
}

