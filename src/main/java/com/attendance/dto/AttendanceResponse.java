package com.attendance.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class AttendanceResponse {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private LocalDateTime timestamp;
    private LocalDate date;
    private LocalTime time;
    private String action;
    private Integer durationMinutes;
    private boolean isWorkingDay;
    private boolean isHoliday;
    private String holidayName;
    private boolean isWeekend;
    private boolean isOvertime;
    private String remarks;
} 