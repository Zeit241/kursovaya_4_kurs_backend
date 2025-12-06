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
 * Фоновая задача для проверки просроченных приёмов
 * Запускается каждую минуту и автоматически меняет статус просроченных приёмов на 'no_show'
 */
@Component
public class AppointmentExpirationTask {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentExpirationTask.class);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;

    public AppointmentExpirationTask(AppointmentRepository appointmentRepository,
                                     AppointmentService appointmentService) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
    }

    /**
     * Проверяет просроченные приёмы каждую минуту
     * Приёмы со статусом 'scheduled' или 'confirmed', у которых endTime < now()
     * автоматически получают статус 'no_show' и удаляются из очереди
     */
    @Scheduled(fixedRate = 60_000) // каждую минуту
    public void checkExpiredAppointments() {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            List<Appointment> expired = appointmentRepository.findExpiredAppointments(now);

            if (expired.isEmpty()) {
                logger.debug("Нет просроченных приёмов");
                return;
            }

            logger.info("Найдено {} просроченных приёмов", expired.size());

            for (Appointment appointment : expired) {
                try {
                    appointmentService.updateAppointmentStatus(appointment.getId(), "no_show");
                    logger.info("Приём ID={} помечен как 'no_show' (время окончания: {})", 
                            appointment.getId(), appointment.getEndTime());
                } catch (Exception e) {
                    logger.error("Ошибка при обновлении статуса приёма ID={}: {}", 
                            appointment.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при проверке просроченных приёмов: {}", e.getMessage(), e);
        }
    }
}



