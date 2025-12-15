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
     * 
     * ОТКЛЮЧЕНО: проверки времени, которые переводят статус приема, убраны
     */
    // @Scheduled(fixedRate = 60_000) // каждую минуту
    public void checkExpiredAppointments() {
        // Проверки времени отключены - статус приема не меняется автоматически
        return;
    }
}



