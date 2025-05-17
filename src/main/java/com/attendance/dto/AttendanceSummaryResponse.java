package com.attendance.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class AttendanceSummaryResponse {
    private List<DailyAttendanceSummary> dailySummaries;
    private DurationSummary totalSummary;
    private Long employeeId;
    private String employeeName;
    private LocalDate fromDate;
    private LocalDate toDate;

    @Data
    @Builder
    public static class DailyAttendanceSummary {
        private LocalDate date;
        private List<AttendanceResponse> attendances;
        private boolean isWorkingDay;
        private boolean isHoliday;
        private boolean isWeekend;
        private double totalHours;
        private double overtimeHours;
        private String holidayName;
    }

    @Data
    @Builder
    public static class DurationSummary {
        private double totalHours;
        private double totalOvertimeHours;
        private int totalWorkingDays;
        private int totalHolidays;
        private int totalWeekends;
    }
} 