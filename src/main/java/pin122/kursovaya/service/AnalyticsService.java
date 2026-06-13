package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import pin122.kursovaya.dto.AttendanceDynamicsDto;
import pin122.kursovaya.dto.DailyAttendanceItemDto;
import pin122.kursovaya.dto.DailyFinancialItemDto;
import pin122.kursovaya.dto.DoctorWorkloadItemDto;
import pin122.kursovaya.dto.FinancialStatsDto;
import pin122.kursovaya.repository.AppointmentRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Аналитика для админ-панели: загрузка врачей, финансы, динамика посещаемости.
 */
@Service
public class AnalyticsService {

    private final AppointmentRepository appointmentRepository;

    public AnalyticsService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Загрузка врачей за период с разбивкой по статусам scheduled, in_progress, completed, cancelled.
     */
    public List<DoctorWorkloadItemDto> getDoctorWorkload(LocalDate startDate, LocalDate endDate) {
        OffsetDateTime start = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        Map<Long, DoctorWorkloadItemDto> byDoctor = new HashMap<>();
        List<Object[]> rows = appointmentRepository.countByDoctorAndStatusAndDateRange(start, end);

        for (Object[] row : rows) {
            Long doctorId = ((Number) row[0]).longValue();
            String displayName = row[1] != null ? row[1].toString() : "Врач #" + doctorId;
            String status = row[2] != null ? row[2].toString() : "";
            int count = ((Number) row[3]).intValue();

            DoctorWorkloadItemDto item = byDoctor.computeIfAbsent(doctorId, id -> {
                DoctorWorkloadItemDto dto = new DoctorWorkloadItemDto();
                dto.setDoctorId(id);
                dto.setDoctorDisplayName(displayName);
                return dto;
            });

            switch (status) {
                case "scheduled":
                case "confirmed":
                    item.setScheduledCount(item.getScheduledCount() + count);
                    break;
                case "in_progress":
                    item.setInProgressCount(item.getInProgressCount() + count);
                    break;
                case "completed":
                    item.setCompletedCount(item.getCompletedCount() + count);
                    break;
                case "cancelled":
                    item.setCancelledCount(item.getCancelledCount() + count);
                    break;
                default:
                    break;
            }
            item.setTotalCount(item.getTotalCount() + count);
        }

        return new ArrayList<>(byDoctor.values());
    }

    /**
     * Финансовая статистика: выручка по завершённым приёмам с услугой.
     */
    public FinancialStatsDto getFinancialStats(LocalDate startDate, LocalDate endDate) {
        OffsetDateTime start = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        BigDecimal totalRevenue = appointmentRepository.sumCompletedServiceRevenue(start, end);
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }
        long completedWithService = appointmentRepository.countCompletedWithService(start, end);

        FinancialStatsDto dto = new FinancialStatsDto();
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        dto.setTotalRevenue(totalRevenue);
        dto.setCompletedCount((int) completedWithService);
        dto.setAverageCheck(completedWithService > 0
                ? totalRevenue.divide(BigDecimal.valueOf(completedWithService), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        List<DailyFinancialItemDto> daily = new ArrayList<>();
        for (Object[] row : appointmentRepository.sumCompletedRevenueByDay(start, end)) {
            DailyFinancialItemDto dayItem = new DailyFinancialItemDto();
            dayItem.setDate(toLocalDate(row[0]));
            dayItem.setRevenue(row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO);
            dayItem.setCompletedCount(row[2] != null ? ((Number) row[2]).intValue() : 0);
            daily.add(dayItem);
        }
        dto.setDailyBreakdown(daily);
        return dto;
    }

    /**
     * Динамика посещаемости по дням за период.
     */
    public AttendanceDynamicsDto getAttendanceDynamics(LocalDate startDate, LocalDate endDate) {
        OffsetDateTime start = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        Map<LocalDate, DailyAttendanceItemDto> byDay = new TreeMap<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            DailyAttendanceItemDto item = new DailyAttendanceItemDto();
            item.setDate(d);
            byDay.put(d, item);
        }

        for (Object[] row : appointmentRepository.countByDayAndStatus(start, end)) {
            LocalDate day = toLocalDate(row[0]);
            String status = row[1] != null ? row[1].toString() : "";
            int count = row[2] != null ? ((Number) row[2]).intValue() : 0;

            DailyAttendanceItemDto item = byDay.computeIfAbsent(day, d -> {
                DailyAttendanceItemDto dto = new DailyAttendanceItemDto();
                dto.setDate(d);
                return dto;
            });

            switch (status) {
                case "scheduled":
                case "confirmed":
                    item.setScheduledCount(item.getScheduledCount() + count);
                    break;
                case "in_progress":
                    item.setInProgressCount(item.getInProgressCount() + count);
                    break;
                case "completed":
                    item.setCompletedCount(item.getCompletedCount() + count);
                    break;
                case "cancelled":
                    item.setCancelledCount(item.getCancelledCount() + count);
                    break;
                default:
                    break;
            }
            item.setTotalCount(item.getTotalCount() + count);
        }

        AttendanceDynamicsDto dto = new AttendanceDynamicsDto();
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        dto.setDailyItems(new ArrayList<>(byDay.values()));
        return dto;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate ld) {
            return ld;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
