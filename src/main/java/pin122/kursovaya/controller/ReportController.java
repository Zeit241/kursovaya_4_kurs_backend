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
 * Контроллер для получения сводных отчётов
 * Доступен только для пользователей с ролью admin
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReportService reportService;
    private final ReportExportService reportExportService;
    private final UserRepository userRepository;

    public ReportController(ReportService reportService, 
                           ReportExportService reportExportService,
                           UserRepository userRepository) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
        this.userRepository = userRepository;
    }

    /**
     * Проверка прав доступа - только admin
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
        
        return user.getRoles().stream()
                .anyMatch(role -> "admin".equalsIgnoreCase(role.getCode()));
    }

    /**
     * Получить перечень всех записанных пациентов на определённую дату
     * 
     * GET /api/reports/daily?date=2024-01-15
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
     * Получить перечень записанных пациентов на определённую дату к определённому врачу
     * 
     * GET /api/reports/daily/doctor/{doctorId}?date=2024-01-15
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
     * Получить перечень записей за период
     * 
     * GET /api/reports/range?startDate=2024-01-01&endDate=2024-01-31
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
     * Получить перечень записей к врачу за период
     * 
     * GET /api/reports/range/doctor/{doctorId}?startDate=2024-01-01&endDate=2024-01-31
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
     * Скачать отчёт за дату в формате Excel
     * 
     * GET /api/reports/daily/excel?date=2024-01-15
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
     * Скачать отчёт за дату в формате PDF
     * 
     * GET /api/reports/daily/pdf?date=2024-01-15
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
     * Скачать отчёт по врачу за дату в формате Excel
     * 
     * GET /api/reports/daily/doctor/{doctorId}/excel?date=2024-01-15
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
     * Скачать отчёт по врачу за дату в формате PDF
     * 
     * GET /api/reports/daily/doctor/{doctorId}/pdf?date=2024-01-15
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
     * Скачать отчёт за период в формате Excel
     * 
     * GET /api/reports/range/excel?startDate=2024-01-01&endDate=2024-01-31
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
     * Скачать отчёт за период в формате PDF
     * 
     * GET /api/reports/range/pdf?startDate=2024-01-01&endDate=2024-01-31
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
     * Скачать отчёт по врачу за период в формате Excel
     * 
     * GET /api/reports/range/doctor/{doctorId}/excel?startDate=2024-01-01&endDate=2024-01-31
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
     * Скачать отчёт по врачу за период в формате PDF
     * 
     * GET /api/reports/range/doctor/{doctorId}/pdf?startDate=2024-01-01&endDate=2024-01-31
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

