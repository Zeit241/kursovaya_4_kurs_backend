package pin122.kursovaya.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Сервис для отправки email писем
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.clinic.name:Медицинский центр}")
    private String clinicName;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    /**
     * Асинхронная отправка HTML письма с использованием шаблона
     *
     * @param to           Email получателя
     * @param subject      Тема письма
     * @param templateName Название шаблона (без расширения)
     * @param context      Контекст с данными для шаблона
     */
    @Async
    public void sendHtmlEmail(String to, String subject, String templateName, Context context) {
        try {
            context.setVariable("clinicName", clinicName);
            
            String htmlContent = templateEngine.process(templateName, context);
            
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
            logger.info("Email успешно отправлен на {}, тема: {}", to, subject);
            
        } catch (MessagingException e) {
            logger.error("Ошибка при отправке email на {}: {}", to, e.getMessage());
        }
    }

    /**
     * Отправка простого текстового письма
     *
     * @param to      Email получателя
     * @param subject Тема письма
     * @param text    Текст письма
     */
    @Async
    public void sendSimpleEmail(String to, String subject, String text) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            
            mailSender.send(mimeMessage);
            logger.info("Простое email успешно отправлено на {}, тема: {}", to, subject);
            
        } catch (MessagingException e) {
            logger.error("Ошибка при отправке простого email на {}: {}", to, e.getMessage());
        }
    }
}

