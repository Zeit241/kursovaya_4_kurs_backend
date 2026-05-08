package pin122.kursovaya.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.DailyReportDto;
import pin122.kursovaya.dto.ReportAppointmentDto;
import pin122.kursovaya.model.Appointment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Генерация двоичных отчётов: сводная таблица приёмов в Excel/PDF и талон на один приём в PDF (iText, кириллица).
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Строит книгу Excel (.xlsx) со статистикой и таблицей приёмов.
     *
     * @param report данные дневного/периодного отчёта
     * @return содержимое файла
     * @throws IOException ошибки записи в {@link ByteArrayOutputStream} или POI
     */
    public byte[] generateExcelReport(DailyReportDto report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Отчёт");

            // Стили
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle statsStyle = createStatsStyle(workbook);

            int rowNum = 0;

            String[] tableHeaders = {
                "№", "Время", "Статус", "Пациент", "Телефон", "Email",
                "Дата рождения", "Пол", "Полис", "Врач", "Кабинет", "Диагноз",
                "Жалобы", "Анамнез", "Рекомендации"
            };

            // Заголовок отчёта
            Row titleRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            String title = "Отчёт за " + report.getDate().format(DATE_FORMATTER);
            if (report.getDoctorDisplayName() != null) {
                title += " - Врач: " + report.getDoctorDisplayName();
            }
            titleCell.setCellValue(title);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, tableHeaders.length - 1));

            rowNum++; // Пустая строка

            // Статистика
            Row statsHeaderRow = sheet.createRow(rowNum++);
            createStatsCell(statsHeaderRow, 0, "Всего записей:", statsStyle);
            createStatsCell(statsHeaderRow, 1, String.valueOf(report.getTotalAppointments()), dataStyle);
            createStatsCell(statsHeaderRow, 3, "Запланировано:", statsStyle);
            createStatsCell(statsHeaderRow, 4, String.valueOf(report.getScheduledCount()), dataStyle);

            Row statsRow2 = sheet.createRow(rowNum++);
            createStatsCell(statsRow2, 0, "Завершено:", statsStyle);
            createStatsCell(statsRow2, 1, String.valueOf(report.getCompletedCount()), dataStyle);
            createStatsCell(statsRow2, 3, "Отменено:", statsStyle);
            createStatsCell(statsRow2, 4, String.valueOf(report.getCancelledCount()), dataStyle);

            Row statsRow3 = sheet.createRow(rowNum++);
            createStatsCell(statsRow3, 0, "Неявки:", statsStyle);
            createStatsCell(statsRow3, 1, String.valueOf(report.getNoShowCount()), dataStyle);

            rowNum++; // Пустая строка

            // Заголовки таблицы
            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < tableHeaders.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(tableHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            // Данные
            int num = 1;
            for (ReportAppointmentDto appointment : report.getAppointments()) {
                Row dataRow = sheet.createRow(rowNum++);

                createDataCell(dataRow, 0, String.valueOf(num++), dataStyle);
                createDataCell(dataRow, 1, formatTimeRange(appointment), dataStyle);
                createDataCell(dataRow, 2, translateStatus(appointment.getStatus()), dataStyle);
                createDataCell(dataRow, 3, appointment.getPatientFullName(), dataStyle);
                createDataCell(dataRow, 4, appointment.getPatientPhone(), dataStyle);
                createDataCell(dataRow, 5, appointment.getPatientEmail(), dataStyle);
                createDataCell(dataRow, 6, appointment.getPatientBirthDate() != null ? 
                        appointment.getPatientBirthDate().format(DATE_FORMATTER) : "", dataStyle);
                createDataCell(dataRow, 7, appointment.getPatientGender(), dataStyle);
                createDataCell(dataRow, 8, appointment.getPatientInsuranceNumber(), dataStyle);
                createDataCell(dataRow, 9, appointment.getDoctorDisplayName(), dataStyle);
                createDataCell(dataRow, 10, appointment.getRoomNumber(), dataStyle);
                createDataCell(dataRow, 11, appointment.getDiagnosis(), dataStyle);
                createDataCell(dataRow, 12, appointment.getComplaints(), dataStyle);
                createDataCell(dataRow, 13, appointment.getAnamnesis(), dataStyle);
                createDataCell(dataRow, 14, appointment.getRecommendations(), dataStyle);
            }

            // Автоподбор ширины колонок
            for (int i = 0; i < tableHeaders.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Формирует PDF в альбомной ориентации с таблицей приёмов (шрифт Arial с поддержкой кириллицы).
     *
     * @param report исходные данные отчёта
     * @return байты PDF
     * @throws IOException ошибки iText или отсутствие файла шрифта по пути ОС
     */
    public byte[] generatePdfReport(DailyReportDto report) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDoc = new PdfDocument(writer);
            pdfDoc.setDefaultPageSize(PageSize.A4.rotate()); // Альбомная ориентация
            Document document = new Document(pdfDoc);

            // Шрифт с поддержкой кириллицы
            PdfFont font = PdfFontFactory.createFont(
                "c:/windows/fonts/arial.ttf", 
                PdfEncodings.IDENTITY_H, 
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
            );
            document.setFont(font);

            // Заголовок
            String title = "Отчёт за " + report.getDate().format(DATE_FORMATTER);
            if (report.getDoctorDisplayName() != null) {
                title += " - Врач: " + report.getDoctorDisplayName();
            }
            Paragraph titleParagraph = new Paragraph(title)
                    .setFontSize(16)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(titleParagraph);

            // Статистика
            Table statsTable = new Table(UnitValue.createPercentArray(new float[]{2, 1, 2, 1, 2, 1}))
                    .setWidth(UnitValue.createPercentValue(80))
                    .setMarginBottom(20);

            addStatsRow(statsTable, font, "Всего записей:", String.valueOf(report.getTotalAppointments()),
                    "Запланировано:", String.valueOf(report.getScheduledCount()),
                    "Завершено:", String.valueOf(report.getCompletedCount()));
            addStatsRow(statsTable, font, "Отменено:", String.valueOf(report.getCancelledCount()),
                    "Неявки:", String.valueOf(report.getNoShowCount()), "", "");

            document.add(statsTable);

            // Таблица данных
            float[] columnWidths = {3, 6, 6, 10, 7, 10, 5, 4, 6, 8, 3, 8, 8, 8, 10};
            Table dataTable = new Table(UnitValue.createPercentArray(columnWidths))
                    .setWidth(UnitValue.createPercentValue(100));

            // Заголовки
            String[] headers = {"№", "Время", "Статус", "Пациент", "Телефон", "Email",
                    "Дата рожд.", "Пол", "Полис", "Врач", "Каб.", "Диагноз", "Жалобы", "Анамнез", "Реком."};
            for (String header : headers) {
                Cell cell = new Cell()
                        .add(new Paragraph(header).setFontSize(8).setBold())
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                        .setTextAlignment(TextAlignment.CENTER);
                dataTable.addHeaderCell(cell);
            }

            // Данные
            int num = 1;
            for (ReportAppointmentDto appointment : report.getAppointments()) {
                addDataCell(dataTable, String.valueOf(num++), font);
                addDataCell(dataTable, formatTimeRange(appointment), font);
                addDataCell(dataTable, translateStatus(appointment.getStatus()), font);
                addDataCell(dataTable, appointment.getPatientFullName(), font);
                addDataCell(dataTable, appointment.getPatientPhone(), font);
                addDataCell(dataTable, appointment.getPatientEmail(), font);
                addDataCell(dataTable, appointment.getPatientBirthDate() != null ?
                        appointment.getPatientBirthDate().format(DATE_FORMATTER) : "", font);
                addDataCell(dataTable, appointment.getPatientGender(), font);
                addDataCell(dataTable, appointment.getPatientInsuranceNumber(), font);
                addDataCell(dataTable, appointment.getDoctorDisplayName(), font);
                addDataCell(dataTable, appointment.getRoomNumber(), font);
                addDataCell(dataTable, appointment.getDiagnosis(), font);
                addDataCell(dataTable, appointment.getComplaints(), font);
                addDataCell(dataTable, appointment.getAnamnesis(), font);
                addDataCell(dataTable, appointment.getRecommendations(), font);
            }

            document.add(dataTable);

            // Дата формирования отчёта
            Paragraph footer = new Paragraph("Отчёт сформирован: " + 
                    java.time.LocalDateTime.now().format(DATETIME_FORMATTER))
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMarginTop(20);
            document.add(footer);

            document.close();
            return outputStream.toByteArray();
        }
    }

    /**
     * Компактный PDF-талон на один {@link Appointment} (формат A5).
     *
     * @param appointment сущность приёма с врачом/пациентом
     * @return байты PDF
     * @throws IOException ошибки iText или шрифта
     */
    public byte[] generateAppointmentPdf(Appointment appointment) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDoc = new PdfDocument(writer);
            pdfDoc.setDefaultPageSize(PageSize.A5); // Маленький формат для талона
            Document document = new Document(pdfDoc);

            // Шрифт с поддержкой кириллицы
            PdfFont font = PdfFontFactory.createFont(
                "c:/windows/fonts/arial.ttf", 
                PdfEncodings.IDENTITY_H, 
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
            );
            document.setFont(font);

            // Заголовок
            Paragraph title = new Paragraph("ТАЛОН НА ПРИЁМ")
                    .setFontSize(18)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(title);

            // Номер записи
            Paragraph appointmentNumber = new Paragraph("№ " + appointment.getId())
                    .setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(appointmentNumber);

            // Информация о записи в таблице
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{40, 60}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginBottom(15);

            // Дата и время
            addInfoRow(infoTable, font, "Дата:", 
                appointment.getStartTime() != null ? 
                    appointment.getStartTime().format(DATE_FORMATTER) : "Не указана");
            addInfoRow(infoTable, font, "Время:", 
                appointment.getStartTime() != null && appointment.getEndTime() != null ?
                    appointment.getStartTime().format(TIME_FORMATTER) + " - " + 
                    appointment.getEndTime().format(TIME_FORMATTER) : "Не указано");

            // Врач
            String doctorName = "Не указан";
            String doctorSpecialization = "";
            if (appointment.getDoctor() != null) {
                doctorName = appointment.getDoctor().getDisplayName();
                if (appointment.getDoctor().getSpecializations() != null && 
                    !appointment.getDoctor().getSpecializations().isEmpty()) {
                    doctorSpecialization = appointment.getDoctor().getSpecializations().get(0)
                            .getSpecialization().getName();
                }
            }
            addInfoRow(infoTable, font, "Врач:", doctorName);
            if (!doctorSpecialization.isEmpty()) {
                addInfoRow(infoTable, font, "Специализация:", doctorSpecialization);
            }

            // Кабинет
            String roomInfo = appointment.getRoom() != null ? 
                "Кабинет № " + appointment.getRoom().getId() : "Будет сообщён дополнительно";
            addInfoRow(infoTable, font, "Кабинет:", roomInfo);

            // Пациент
            String patientName = "Не указан";
            if (appointment.getPatient() != null && appointment.getPatient().getUser() != null) {
                var user = appointment.getPatient().getUser();
                patientName = String.join(" ", 
                    user.getLastName() != null ? user.getLastName() : "",
                    user.getFirstName() != null ? user.getFirstName() : "",
                    user.getMiddleName() != null ? user.getMiddleName() : ""
                ).trim();
                if (patientName.isEmpty()) {
                    patientName = "Пациент";
                }
            }
            addInfoRow(infoTable, font, "Пациент:", patientName);

            // Статус
            addInfoRow(infoTable, font, "Статус:", translateStatus(appointment.getStatus()));

            if (appointment.getComplaints() != null && !appointment.getComplaints().isBlank()) {
                addInfoRow(infoTable, font, "Жалобы:", appointment.getComplaints());
            }
            if (appointment.getAnamnesis() != null && !appointment.getAnamnesis().isBlank()) {
                addInfoRow(infoTable, font, "Анамнез:", appointment.getAnamnesis());
            }
            if (appointment.getRecommendations() != null && !appointment.getRecommendations().isBlank()) {
                addInfoRow(infoTable, font, "Назначения:", appointment.getRecommendations());
            }

            document.add(infoTable);

            // Линия разделитель
            document.add(new Paragraph("─".repeat(50))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(10)
                    .setMarginBottom(10));

            // Информационный блок
            Paragraph info = new Paragraph("Пожалуйста, приходите за 10 минут до назначенного времени. " +
                    "При себе иметь паспорт и полис ОМС.")
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(info);

            // Дата формирования
            Paragraph footer = new Paragraph("Талон сформирован: " + 
                    java.time.LocalDateTime.now().format(DATETIME_FORMATTER))
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMarginTop(30);
            document.add(footer);

            document.close();
            return outputStream.toByteArray();
        }
    }

    /**
     * Добавляет строку «метка — значение» в PDF-таблицу талона.
     *
     * @param table таблица iText
     * @param font  шрифт
     * @param label подпись поля
     * @param value значение
     */
    private void addInfoRow(Table table, PdfFont font, String label, String value) {
        Cell labelCell = new Cell()
                .add(new Paragraph(label).setFont(font).setFontSize(11).setBold())
                .setBorder(null)
                .setPaddingBottom(5);
        table.addCell(labelCell);

        Cell valueCell = new Cell()
                .add(new Paragraph(value != null ? value : "").setFont(font).setFontSize(11))
                .setBorder(null)
                .setPaddingBottom(5);
        table.addCell(valueCell);
    }

    /**
     * Стиль заголовков колонок Excel.
     *
     * @param workbook книга
     * @return стиль ячейки
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * Стиль заголовка листа отчёта.
     *
     * @param workbook книга
     * @return стиль
     */
    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * Стиль ячеек с данными таблицы.
     *
     * @param workbook книга
     * @return стиль
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    /**
     * Стиль подписей блока статистики.
     *
     * @param workbook книга
     * @return стиль
     */
    private CellStyle createStatsStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    /**
     * Создаёт ячейку статистики в заданной колонке строки.
     *
     * @param row    строка Excel
     * @param column индекс колонки
     * @param value  текст
     * @param style  стиль
     */
    private void createStatsCell(Row row, int column, String value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /**
     * Ячейка данных в таблице приёмов.
     *
     * @param row    строка
     * @param column колонка
     * @param value  значение
     * @param style  стиль
     */
    private void createDataCell(Row row, int column, String value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /**
     * Добавляет в PDF-таблицу статистики шесть ячеек из переданных строк.
     *
     * @param table  таблица
     * @param font   шрифт
     * @param values до шести подписей/значений
     */
    private void addStatsRow(Table table, PdfFont font, String... values) {
        for (String value : values) {
            Cell cell = new Cell()
                    .add(new Paragraph(value != null ? value : "").setFont(font).setFontSize(10))
                    .setBorder(null);
            table.addCell(cell);
        }
    }

    /**
     * Ячейка данных в PDF-таблице отчёта.
     *
     * @param table таблица
     * @param value текст
     * @param font  шрифт
     */
    private void addDataCell(Table table, String value, PdfFont font) {
        Cell cell = new Cell()
                .add(new Paragraph(value != null ? value : "").setFont(font).setFontSize(7))
                .setTextAlignment(TextAlignment.CENTER);
        table.addCell(cell);
    }

    /**
     * Интервал времени приёма для колонки «Время».
     *
     * @param appointment строка отчёта
     * @return {@code HH:mm-HH:mm} или пустая строка
     */
    private String formatTimeRange(ReportAppointmentDto appointment) {
        if (appointment.getStartTime() == null) return "";
        String start = appointment.getStartTime().format(TIME_FORMATTER);
        String end = appointment.getEndTime() != null ? appointment.getEndTime().format(TIME_FORMATTER) : "";
        return start + "-" + end;
    }

    /**
     * Локализация кода статуса приёма для печати.
     *
     * @param status код
     * @return русская подпись или исходная строка
     */
    private String translateStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "scheduled" -> "Запланировано";
            case "confirmed" -> "Подтверждено";
            case "in_progress" -> "В процессе";
            case "completed" -> "Завершено";
            case "cancelled" -> "Отменено";
            case "no_show" -> "Неявка";
            default -> status;
        };
    }
}



