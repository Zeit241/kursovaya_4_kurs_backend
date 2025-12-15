package pin122.kursovaya.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.repository.AppointmentRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Фоновая задача для отправки напоминаний о предстоящих приёмах
 * Запускается каждый день в 9:00 и отправляет напоминания о приёмах на следующий день
 */
@Component
public class AppointmentReminderTask {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentReminderTask.class);

    private final AppointmentRepository appointmentRepository;
    private final EmailNotificationService emailNotificationService;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    public AppointmentReminderTask(AppointmentRepository appointmentRepository,
                                   EmailNotificationService emailNotificationService) {
        this.appointmentRepository = appointmentRepository;
        this.emailNotificationService = emailNotificationService;
    }

    /**
     * Отправляет напоминания о приёмах на следующий день
     * Запускается каждый день в 9:00
     */
    @Scheduled(cron = "0 0 9 * * *") // каждый день в 9:00
    public void sendDailyReminders() {
        if (!notificationsEnabled) {
            logger.debug("Уведомления отключены, пропускаем отправку напоминаний");
            return;
        }

        try {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            OffsetDateTime startOfTomorrow = tomorrow.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime endOfTomorrow = tomorrow.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

            List<Appointment> tomorrowAppointments = appointmentRepository
                    .findScheduledAppointmentsBetween(startOfTomorrow, endOfTomorrow);

            if (tomorrowAppointments.isEmpty()) {
                logger.debug("Нет приёмов на завтра для отправки напоминаний");
                return;
            }

            logger.info("Найдено {} приёмов на завтра, отправляем напоминания", tomorrowAppointments.size());

            int successCount = 0;
            int failCount = 0;

            for (Appointment appointment : tomorrowAppointments) {
                try {
                    if (appointment.getPatient() != null) {
                        emailNotificationService.sendAppointmentReminderNotification(appointment);
                        successCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    logger.error("Ошибка при отправке напоминания для приёма ID={}: {}", 
                            appointment.getId(), e.getMessage());
                }
            }

            logger.info("Отправка напоминаний завершена: успешно={}, ошибки={}", successCount, failCount);
            
        } catch (Exception e) {
            logger.error("Ошибка при отправке ежедневных напоминаний: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправляет напоминания за 2 часа до приёма
     * Запускается каждые 30 минут
     */
    @Scheduled(fixedRate = 1800000) // каждые 30 минут
    public void sendUpcomingReminders() {
        if (!notificationsEnabled) {
            return;
        }

        try {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime inTwoHours = now.plusHours(2);
            OffsetDateTime inTwoAndHalfHours = now.plusHours(2).plusMinutes(30);

            // Ищем приёмы, которые начнутся через 2-2.5 часа
            List<Appointment> upcomingAppointments = appointmentRepository
                    .findScheduledAppointmentsBetween(inTwoHours, inTwoAndHalfHours);

            for (Appointment appointment : upcomingAppointments) {
                try {
                    if (appointment.getPatient() != null) {
                        emailNotificationService.sendAppointmentReminderNotification(appointment);
                        logger.info("Отправлено напоминание за 2 часа для приёма ID={}", appointment.getId());
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при отправке напоминания для приёма ID={}: {}", 
                            appointment.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при проверке предстоящих приёмов: {}", e.getMessage(), e);
        }
    }
}

