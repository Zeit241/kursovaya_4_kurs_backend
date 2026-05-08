package pin122.kursovaya.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.DailyReportDto;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.service.ReportExportService;
import pin122.kursovaya.service.ReportService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * REST-контроллер сводных отчётов по записям на приём и выгрузки в Excel/PDF.
 * <p>
 * Базовый путь: {@code /api/reports}. Доступ только у пользователей с ролью {@code admin}.
 *
 * @see ReportService
 * @see ReportExportService
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReportService reportService;
    private final ReportExportService reportExportService;
    private final UserRepository userRepository;

    /**
     * @param reportService         агрегация данных отчётов
     * @param reportExportService   генерация файлов Excel и PDF
     * @param userRepository        проверка роли администратора по email из {@link Authentication}
     */
    public ReportController(ReportService reportService, 
                           ReportExportService reportExportService,
                           UserRepository userRepository) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
        this.userRepository = userRepository;
    }

    /**
     * Проверяет, что в {@link Authentication} указан пользователь с ролью администратора.
     *
     * @param authentication контекст Spring Security или {@code null}
     * @return {@code true}, если пользователь найден в БД и его роль — {@code admin}
     */
    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        String email = authentication.getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            return false;
        }
        
        return user.getRole() != null && "admin".equalsIgnoreCase(user.getRole().getCode());
    }

    /**
     * Возвращает перечень всех записей на указанную дату.
     *
     * @param date             дата отчёта в формате ISO
     * @param authentication   контекст безопасности
     * @return HTTP 200 и {@link DailyReportDto}; HTTP 403 если не админ
     * @apiNote {@code GET /api/reports/daily?date=}
     */
    @GetMapping("/daily")
    public ResponseEntity<?> getDailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        DailyReportDto report = reportService.getAllAppointmentsByDate(date);
        return ResponseEntity.ok(report);
    }

    /**
     * Возвращает записи на дату для одного врача.
     *
     * @param doctorId         идентификатор врача
     * @param date             дата отчёта
     * @param authentication   контекст безопасности
     * @return HTTP 200 и {@link DailyReportDto}; HTTP 403 если не админ
     */
    @GetMapping("/daily/doctor/{doctorId}")
    public ResponseEntity<?> getDailyReportByDoctor(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        DailyReportDto report = reportService.getAppointmentsByDoctorAndDate(doctorId, date);
        return ResponseEntity.ok(report);
    }

    /**
     * Возвращает записи за диапазон дат включительно.
     *
     * @param startDate        начало периода
     * @param endDate          конец периода
     * @param authentication   контекст безопасности
     * @return HTTP 200 и отчёт; HTTP 403 или HTTP 400 при {@code startDate} после {@code endDate}
     */
    @GetMapping("/range")
    public ResponseEntity<?> getReportByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Дата начала не может быть позже даты окончания."));
        }
        
        DailyReportDto report = reportService.getAppointmentsByDateRange(startDate, endDate);
        return ResponseEntity.ok(report);
    }

    /**
     * Возвращает записи к врачу за диапазон дат.
     *
     * @param doctorId         идентификатор врача
     * @param startDate        начало периода
     * @param endDate          конец периода
     * @param authentication   контекст безопасности
     * @return HTTP 200 и отчёт; HTTP 403 или HTTP 400 при неверном диапазоне
     */
    @GetMapping("/range/doctor/{doctorId}")
    public ResponseEntity<?> getReportByDoctorAndDateRange(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Дата начала не может быть позже даты окончания."));
        }
        
        DailyReportDto report = reportService.getAppointmentsByDoctorAndDateRange(doctorId, startDate, endDate);
        return ResponseEntity.ok(report);
    }

    // ==================== ЭКСПОРТ В ФАЙЛЫ ====================

    /**
     * Формирует и отдаёт Excel-файл отчёта за одну дату.
     *
     * @param date             дата отчёта
     * @param authentication   контекст безопасности
     * @return HTTP 200 с телом {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}; HTTP 403/500 при ошибках
     */
    @GetMapping("/daily/excel")
    public ResponseEntity<?> downloadDailyReportExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        try {
            DailyReportDto report = reportService.getAllAppointmentsByDate(date);
            byte[] excelContent = reportExportService.generateExcelReport(report);
            
            String filename = "report_" + date.format(DATE_FORMATTER) + ".xlsx";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + 
                            URLEncoder.encode(filename, StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelContent);
        } catch (IOException e) {
            logger.error("Ошибка при генерации Excel отчёта", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка при формировании отчёта: " + e.getMessage()));
        }
    }

    /**
     * Формирует и отдаёт PDF-файл дневного отчёта по всем записям на указанную дату.
     *
     * @param date             дата отчёта в формате ISO
     * @param authentication   контекст безопасности
     * @return HTTP 200 с телом {@code application/pdf} и заголовком вложения; HTTP 403 если не админ;
     *         HTTP 500 при ошибке генерации PDF
     * @apiNote {@code GET /api/reports/daily/pdf?date=}
     */
    @GetMapping("/daily/pdf")
    public ResponseEntity<?> downloadDailyReportPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        try {
            DailyReportDto report = reportService.getAllAppointmentsByDate(date);
            byte[] pdfContent = reportExportService.generatePdfReport(report);
            
            String filename = "report_" + date.format(DATE_FORMATTER) + ".pdf";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + 
                            URLEncoder.encode(filename, StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfContent);
        } catch (IOException e) {
            logger.error("Ошибка при генерации PDF отчёта", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка при формировании отчёта: " + e.getMessage()));
        }
    }

    /**
     * Формирует и отдаёт Excel-файл отчёта по записям одного врача на указанную дату.
     *
     * @param doctorId         идентификатор врача
     * @param date             дата отчёта в формате ISO
     * @param authentication   контекст безопасности
     * @return HTTP 200 с телом {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet};
     *         HTTP 403 если не админ; HTTP 500 при ошибке генерации Excel
     * @apiNote {@code GET /api/reports/daily/doctor/{doctorId}/excel?date=}
     */
    @GetMapping("/daily/doctor/{doctorId}/excel")
    public ResponseEntity<?> downloadDailyDoctorReportExcel(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        try {
            DailyReportDto report = reportService.getAppointmentsByDoctorAndDate(doctorId, date);
            byte[] excelContent = reportExportService.generateExcelReport(report);
            
            String filename = "report_doctor" + doctorId + "_" + date.format(DATE_FORMATTER) + ".xlsx";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + 
                            URLEncoder.encode(filename, StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelContent);
        } catch (IOException e) {
            logger.error("Ошибка при генерации Excel отчёта", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка при формировании отчёта: " + e.getMessage()));
        }
    }

    /**
     * Формирует и отдаёт PDF-файл отчёта по записям одного врача на указанную дату.
     *
     * @param doctorId         идентификатор врача
     * @param date             дата отчёта в формате ISO
     * @param authentication   контекст безопасности
     * @return HTTP 200 с телом {@code application/pdf} и заголовком вложения; HTTP 403 если не админ;
     *         HTTP 500 при ошибке генерации PDF
     * @apiNote {@code GET /api/reports/daily/doctor/{doctorId}/pdf?date=}
     */
    @GetMapping("/daily/doctor/{doctorId}/pdf")
    public ResponseEntity<?> downloadDailyDoctorReportPdf(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        try {
            DailyReportDto report = reportService.getAppointmentsByDoctorAndDate(doctorId, date);
            byte[] pdfContent = reportExportService.generatePdfReport(report);
            
            String filename = "report_doctor" + doctorId + "_" + date.format(DATE_FORMATTER) + ".pdf";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + 
                            URLEncoder.encode(filename, StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfContent);
        } catch (IOException e) {
            logger.error("Ошибка при генерации PDF отчёта", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка при формировании отчёта: " + e.getMessage()));
        }
    }

    /**
     * Формирует и отдаёт Excel-файл отчёта по всем записям за период дат включительно.
     *
     * @param startDate        начало периода
     * @param endDate          конец периода
     * @param authentication   контекст безопасности
     * @return HTTP 200 с телом Excel; HTTP 403 если не админ; HTTP 400 если {@code startDate} позже {@code endDate};
     *         HTTP 500 при ошибке генерации файла
     * @apiNote {@code GET /api/reports/range/excel?startDate=&endDate=}
     */
    @GetMapping("/range/excel")
    public ResponseEntity<?> downloadRangeReportExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Дата начала не может быть позже даты окончания."));
        }
        
        try {
            DailyReportDto report = reportService.getAppointmentsByDateRange(startDate, endDate);
            byte[] excelContent = reportExportService.generateExcelReport(report);
            
            String filename = "report_" + startDate.format(DATE_FORMATTER) + "_" + endDate.format(DATE_FORMATTER) + ".xlsx";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + 
                            URLEncoder.encode(filename, StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelContent);
        } catch (IOException e) {
            logger.error("Ошибка при генерации Excel отчёта", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка при формировании отчёта: " + e.getMessage()));
        }
    }

    /**
     * Формирует и отдаёт PDF-файл отчёта по всем записям за период дат включительно.
     *
     * @param startDate        начало периода
     * @param endDate          конец периода
     * @param authentication   контекст безопасности
     * @return HTTP 200 с телом {@code application/pdf}; HTTP 403 если не админ; HTTP 400 при неверном диапазоне;
     *         HTTP 500 при ошибке генерации PDF
     * @apiNote {@code GET /api/reports/range/pdf?startDate=&endDate=}
     */
    @GetMapping("/range/pdf")
    public ResponseEntity<?> downloadRangeReportPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Дата начала не может быть позже даты окончания."));
        }
        
        try {
            DailyReportDto report = reportService.getAppointmentsByDateRange(startDate, endDate);
            byte[] pdfContent = reportExportService.generatePdfReport(report);
            
            String filename = "report_" + startDate.format(DATE_FORMATTER) + "_" + endDate.format(DATE_FORMATTER) + ".pdf";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + 
                            URLEncoder.encode(filename, StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfContent);
        } catch (IOException e) {
            logger.error("Ошибка при генерации PDF отчёта", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка при формировании отчёта: " + e.getMessage()));
        }
    }

    /**
     * Формирует и отдаёт Excel-файл отчёта по записям одного врача за период дат включительно.
     *
     * @param doctorId         идентификатор врача
     * @param startDate        начало периода
     * @param endDate          конец периода
     * @param authentication   контекст безопасности
     * @return HTTP 200 с телом Excel; HTTP 403 если не админ; HTTP 400 при неверном диапазоне; HTTP 500 при ошибке генерации
     * @apiNote {@code GET /api/reports/range/doctor/{doctorId}/excel?startDate=&endDate=}
     */
    @GetMapping("/range/doctor/{doctorId}/excel")
    public ResponseEntity<?> downloadRangeDoctorReportExcel(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Дата начала не может быть позже даты окончания."));
        }
        
        try {
            DailyReportDto report = reportService.getAppointmentsByDoctorAndDateRange(doctorId, startDate, endDate);
            byte[] excelContent = reportExportService.generateExcelReport(report);
            
            String filename = "report_doctor" + doctorId + "_" + startDate.format(DATE_FORMATTER) + "_" + endDate.format(DATE_FORMATTER) + ".xlsx";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + 
                            URLEncoder.encode(filename, StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelContent);
        } catch (IOException e) {
            logger.error("Ошибка при генерации Excel отчёта", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка при формировании отчёта: " + e.getMessage()));
        }
    }

    /**
     * Формирует и отдаёт PDF-файл отчёта по записям одного врача за период дат включительно.
     *
     * @param doctorId         идентификатор врача
     * @param startDate        начало периода
     * @param endDate          конец периода
     * @param authentication   контекст безопасности
     * @return HTTP 200 с телом {@code application/pdf}; HTTP 403 если не админ; HTTP 400 при неверном диапазоне;
     *         HTTP 500 при ошибке генерации PDF
     * @apiNote {@code GET /api/reports/range/doctor/{doctorId}/pdf?startDate=&endDate=}
     */
    @GetMapping("/range/doctor/{doctorId}/pdf")
    public ResponseEntity<?> downloadRangeDoctorReportPdf(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён. Требуется роль администратора."));
        }
        
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Дата начала не может быть позже даты окончания."));
        }
        
        try {
            DailyReportDto report = reportService.getAppointmentsByDoctorAndDateRange(doctorId, startDate, endDate);
            byte[] pdfContent = reportExportService.generatePdfReport(report);
            
            String filename = "report_doctor" + doctorId + "_" + startDate.format(DATE_FORMATTER) + "_" + endDate.format(DATE_FORMATTER) + ".pdf";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + 
                            URLEncoder.encode(filename, StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfContent);
        } catch (IOException e) {
            logger.error("Ошибка при генерации PDF отчёта", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка при формировании отчёта: " + e.getMessage()));
        }
    }
}

