package pin122.kursovaya.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Notification;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.NotificationRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Сервис для отправки email уведомлений о записях на приём
 */
@Service
public class EmailNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);

    private final EmailService emailService;
    private final NotificationRepository notificationRepository;

    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));
    private static final DateTimeFormatter TIME_FORMATTER = 
            DateTimeFormatter.ofPattern("HH:mm");

    public EmailNotificationService(EmailService emailService, 
                                   NotificationRepository notificationRepository) {
        this.emailService = emailService;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Уведомление о новой записи на приём
     */
    public void sendAppointmentBookedNotification(Appointment appointment) {
        Patient patient = appointment.getPatient();
        if (patient == null || patient.getUser() == null) {
            logger.warn("Невозможно отправить уведомление: пациент или пользователь не найден для записи {}", 
                       appointment.getId());
            return;
        }

        User user = patient.getUser();
        String email = user.getEmail();
        
        if (email == null || email.isBlank()) {
            logger.warn("Email не указан для пользователя {}", user.getId());
            return;
        }

        try {
            Context context = new Context();
            context.setVariable("patientName", getPatientFullName(user));
            context.setVariable("doctorName", getDoctorName(appointment));
            context.setVariable("specialization", getDoctorSpecialization(appointment));
            context.setVariable("appointmentDate", formatDate(appointment.getStartTime()));
            context.setVariable("appointmentTime", formatTime(appointment.getStartTime()));
            context.setVariable("roomNumber", getRoomNumber(appointment));

            String subject = "Подтверждение записи на приём";
            
            emailService.sendHtmlEmail(email, subject, "appointment-booked", context);
            
            // Сохраняем уведомление в БД
            saveNotification(user, appointment, "APPOINTMENT_BOOKED", "sent");
            
            logger.info("Отправлено уведомление о записи на приём пользователю {}", email);
            
        } catch (Exception e) {
            logger.error("Ошибка при отправке уведомления о записи: {}", e.getMessage());
            saveNotification(user, appointment, "APPOINTMENT_BOOKED", "failed");
        }
    }

    /**
     * Уведомление об изменении статуса приёма
     */
    public void sendAppointmentStatusChangedNotification(Appointment appointment, String oldStatus, String newStatus) {
        Patient patient = appointment.getPatient();
        if (patient == null || patient.getUser() == null) {
            logger.warn("Невозможно отправить уведомление: пациент не найден для записи {}", 
                       appointment.getId());
            return;
        }

        User user = patient.getUser();
        String email = user.getEmail();
        
        if (email == null || email.isBlank()) {
            logger.warn("Email не указан для пользователя {}", user.getId());
            return;
        }

        try {
            Context context = new Context();
            context.setVariable("patientName", getPatientFullName(user));
            context.setVariable("doctorName", getDoctorName(appointment));
            context.setVariable("specialization", getDoctorSpecialization(appointment));
            context.setVariable("appointmentDate", formatDate(appointment.getStartTime()));
            context.setVariable("appointmentTime", formatTime(appointment.getStartTime()));
            context.setVariable("oldStatus", translateStatus(oldStatus));
            context.setVariable("newStatus", translateStatus(newStatus));
            context.setVariable("statusClass", getStatusClass(newStatus));

            String subject = "Изменение статуса записи на приём";
            
            emailService.sendHtmlEmail(email, subject, "appointment-status-changed", context);
            
            // Сохраняем уведомление в БД
            saveNotification(user, appointment, "STATUS_CHANGED", "sent");
            
            logger.info("Отправлено уведомление об изменении статуса пользователю {}", email);
            
        } catch (Exception e) {
            logger.error("Ошибка при отправке уведомления об изменении статуса: {}", e.getMessage());
            saveNotification(user, appointment, "STATUS_CHANGED", "failed");
        }
    }

    /**
     * Уведомление об отмене приёма
     */
    public void sendAppointmentCancelledNotification(Appointment appointment, String cancelReason) {
        Patient patient = appointment.getPatient();
        if (patient == null || patient.getUser() == null) {
            logger.warn("Невозможно отправить уведомление: пациент не найден для записи {}", 
                       appointment.getId());
            return;
        }

        User user = patient.getUser();
        String email = user.getEmail();
        
        if (email == null || email.isBlank()) {
            logger.warn("Email не указан для пользователя {}", user.getId());
            return;
        }

        try {
            Context context = new Context();
            context.setVariable("patientName", getPatientFullName(user));
            context.setVariable("doctorName", getDoctorName(appointment));
            context.setVariable("specialization", getDoctorSpecialization(appointment));
            context.setVariable("appointmentDate", formatDate(appointment.getStartTime()));
            context.setVariable("appointmentTime", formatTime(appointment.getStartTime()));
            context.setVariable("cancelReason", cancelReason != null ? cancelReason : "Причина не указана");

            String subject = "Отмена записи на приём";
            
            emailService.sendHtmlEmail(email, subject, "appointment-cancelled", context);
            
            // Сохраняем уведомление в БД
            saveNotification(user, appointment, "APPOINTMENT_CANCELLED", "sent");
            
            logger.info("Отправлено уведомление об отмене приёма пользователю {}", email);
            
        } catch (Exception e) {
            logger.error("Ошибка при отправке уведомления об отмене: {}", e.getMessage());
            saveNotification(user, appointment, "APPOINTMENT_CANCELLED", "failed");
        }
    }

    /**
     * Напоминание о предстоящем приёме (для использования в scheduled task)
     */
    public void sendAppointmentReminderNotification(Appointment appointment) {
        Patient patient = appointment.getPatient();
        if (patient == null || patient.getUser() == null) {
            return;
        }

        User user = patient.getUser();
        String email = user.getEmail();
        
        if (email == null || email.isBlank()) {
            return;
        }

        try {
            Context context = new Context();
            context.setVariable("patientName", getPatientFullName(user));
            context.setVariable("doctorName", getDoctorName(appointment));
            context.setVariable("specialization", getDoctorSpecialization(appointment));
            context.setVariable("appointmentDate", formatDate(appointment.getStartTime()));
            context.setVariable("appointmentTime", formatTime(appointment.getStartTime()));
            context.setVariable("roomNumber", getRoomNumber(appointment));

            String subject = "Напоминание о записи на приём";
            
            emailService.sendHtmlEmail(email, subject, "appointment-reminder", context);
            
            saveNotification(user, appointment, "APPOINTMENT_REMINDER", "sent");
            
            logger.info("Отправлено напоминание о приёме пользователю {}", email);
            
        } catch (Exception e) {
            logger.error("Ошибка при отправке напоминания: {}", e.getMessage());
            saveNotification(user, appointment, "APPOINTMENT_REMINDER", "failed");
        }
    }

    /**
     * Уведомление о завершении приёма с просьбой оставить отзыв
     */
    public void sendAppointmentCompletedNotification(Appointment appointment) {
        Patient patient = appointment.getPatient();
        if (patient == null || patient.getUser() == null) {
            return;
        }

        User user = patient.getUser();
        String email = user.getEmail();
        
        if (email == null || email.isBlank()) {
            return;
        }

        try {
            Context context = new Context();
            context.setVariable("patientName", getPatientFullName(user));
            context.setVariable("doctorName", getDoctorName(appointment));
            context.setVariable("specialization", getDoctorSpecialization(appointment));
            context.setVariable("appointmentDate", formatDate(appointment.getStartTime()));

            String subject = "Спасибо за визит! Оставьте отзыв";
            
            emailService.sendHtmlEmail(email, subject, "appointment-completed", context);
            
            saveNotification(user, appointment, "APPOINTMENT_COMPLETED", "sent");
            
            logger.info("Отправлено уведомление о завершении приёма пользователю {}", email);
            
        } catch (Exception e) {
            logger.error("Ошибка при отправке уведомления о завершении: {}", e.getMessage());
            saveNotification(user, appointment, "APPOINTMENT_COMPLETED", "failed");
        }
    }

    // === Вспомогательные методы ===

    private String getPatientFullName(User user) {
        StringBuilder name = new StringBuilder();
        if (user.getLastName() != null) {
            name.append(user.getLastName());
        }
        if (user.getFirstName() != null) {
            if (name.length() > 0) name.append(" ");
            name.append(user.getFirstName());
        }
        if (user.getMiddleName() != null) {
            if (name.length() > 0) name.append(" ");
            name.append(user.getMiddleName());
        }
        return name.length() > 0 ? name.toString() : "Пациент";
    }

    private String getDoctorName(Appointment appointment) {
        if (appointment.getDoctor() != null) {
            return appointment.getDoctor().getDisplayName();
        }
        return "Врач не указан";
    }

    private String getDoctorSpecialization(Appointment appointment) {
        if (appointment.getDoctor() != null && 
            appointment.getDoctor().getSpecializations() != null &&
            !appointment.getDoctor().getSpecializations().isEmpty()) {
            return appointment.getDoctor().getSpecializations().get(0)
                    .getSpecialization().getName();
        }
        return "";
    }

    private String getRoomNumber(Appointment appointment) {
        if (appointment.getRoom() != null) {
            return "Кабинет " + appointment.getRoom().getId();
        }
        return "Будет сообщён дополнительно";
    }

    private String formatDate(OffsetDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DATE_FORMATTER);
    }

    private String formatTime(OffsetDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(TIME_FORMATTER);
    }

    private String translateStatus(String status) {
        if (status == null) return "Неизвестно";
        return switch (status.toLowerCase()) {
            case "available" -> "Доступен";
            case "scheduled" -> "Запланирован";
            case "confirmed" -> "Подтверждён";
            case "in_progress" -> "В процессе";
            case "completed" -> "Завершён";
            case "cancelled" -> "Отменён";
            case "no_show" -> "Неявка";
            case "waiting" -> "Ожидание";
            default -> status;
        };
    }

    private String getStatusClass(String status) {
        if (status == null) return "status-default";
        return switch (status.toLowerCase()) {
            case "scheduled", "confirmed" -> "status-success";
            case "in_progress", "waiting" -> "status-warning";
            case "completed" -> "status-info";
            case "cancelled", "no_show" -> "status-danger";
            default -> "status-default";
        };
    }

    private void saveNotification(User user, Appointment appointment, String type, String status) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("appointmentId", appointment.getId());
            payload.put("doctorName", getDoctorName(appointment));
            payload.put("appointmentDate", appointment.getStartTime() != null ? 
                       appointment.getStartTime().toString() : null);
            
            Notification notification = new Notification(user, appointment, type, payload, status);
            notification.setSentAt(OffsetDateTime.now());
            
            notificationRepository.save(notification);
        } catch (Exception e) {
            logger.error("Ошибка при сохранении уведомления в БД: {}", e.getMessage());
        }
    }
}

