package com.attendance.service.impl;

import com.attendance.dto.AttendanceRequest;
import com.attendance.dto.AttendanceResponse;
import com.attendance.dto.AttendanceSummaryResponse;
import com.attendance.entity.Attendance;
import com.attendance.entity.Employee;
import com.attendance.enums.AttendanceEvent;
import com.attendance.exception.InvalidAttendanceException;
import com.attendance.exception.ResourceNotFoundException;
import com.attendance.repository.AttendanceRepository;
import com.attendance.repository.EmployeeRepository;
import com.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {
    private static final Logger logger = LoggerFactory.getLogger(AttendanceServiceImpl.class);
    private static final LocalTime WORK_START_TIME = LocalTime.of(9, 0); // 9:00 AM
    private static final LocalTime WORK_END_TIME = LocalTime.of(17, 0); // 5:00 PM

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional
    public AttendanceResponse markAttendance(Long employeeId, AttendanceRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        LocalDateTime timestamp = request.getTimestamp();
        LocalDate date = timestamp.toLocalDate();
        LocalTime time = timestamp.toLocalTime();

        // Validate timestamp is not in the future
        if (timestamp.isAfter(LocalDateTime.now())) {
            throw new InvalidAttendanceException("Cannot mark attendance for future timestamp");
        }

        // Check for duplicate punch-in/out
        boolean isPunchIn = "PUNCH_IN".equals(request.getAction());
        boolean isPunchOut = "PUNCH_OUT".equals(request.getAction());
        
        if (!isPunchIn && !isPunchOut) {
            throw new InvalidAttendanceException("Invalid action. Must be either PUNCH_IN or PUNCH_OUT");
        }

        List<Attendance> existingEntries = attendanceRepository.findByEmployeeIdAndDate(employeeId, date);
        
        if (isPunchIn) {
            // Check if already punched in
            boolean alreadyPunchedIn = existingEntries.stream()
                    .anyMatch(entry -> "PUNCH_IN".equals(entry.getAction()));
            if (alreadyPunchedIn) {
                throw new InvalidAttendanceException("Employee already punched in for today");
            }
        } else { // PUNCH_OUT
            // Check if already punched out
            boolean alreadyPunchedOut = existingEntries.stream()
                    .anyMatch(entry -> "PUNCH_OUT".equals(entry.getAction()));
            if (alreadyPunchedOut) {
                throw new InvalidAttendanceException("Employee already punched out for today");
            }
            
            // Check if there's a punch-in for today
            boolean hasPunchIn = existingEntries.stream()
                    .anyMatch(entry -> "PUNCH_IN".equals(entry.getAction()));
            if (!hasPunchIn) {
                throw new InvalidAttendanceException("Cannot punch out without a punch-in record");
            }
        }

        // Check if it's a weekend
        boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;

        // Check if it's a holiday (you can implement your own holiday checking logic)
        boolean isHoliday = false; // Implement holiday checking logic
        String holidayName = null; // Set holiday name if it's a holiday

        // Check if it's overtime (after 6 PM)
        boolean isOvertime = time.isAfter(LocalTime.of(18, 0));

        // Calculate duration if it's a punch-out
        Integer durationMinutes = null;
        if (isPunchOut) {
            Attendance lastPunchIn = existingEntries.stream()
                    .filter(entry -> "PUNCH_IN".equals(entry.getAction()))
                    .findFirst()
                    .orElseThrow(() -> new InvalidAttendanceException("No punch-in record found for today"));
            
            durationMinutes = (int) Duration.between(lastPunchIn.getTimestamp(), timestamp).toMinutes();
        }

        Attendance attendance = Attendance.builder()
                .employee(employee)
                .timestamp(timestamp)
                .date(date)
                .time(time)
                .action(AttendanceEvent.valueOf(request.getAction()))
                .durationMinutes(durationMinutes != null ? (long) durationMinutes : null)
                .isWorkingDay(!isWeekend && !isHoliday)
                .isHoliday(isHoliday)
                .holidayName(holidayName)
                .isWeekend(isWeekend)
                .isOvertime(isOvertime)
                .remarks(request.getRemarks())
                .build();

        Attendance savedAttendance = attendanceRepository.save(attendance);
        return mapToResponse(savedAttendance);
    }

    @Override
    public List<AttendanceResponse> getAttendanceByEmployeeId(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee not found with id: " + employeeId);
        }
        return attendanceRepository.findByEmployeeId(employeeId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AttendanceSummaryResponse getAttendanceForDuration(Long employeeId, LocalDateTime from, LocalDateTime to) {
        logger.info("Fetching attendance summary for employeeId: {} from: {} to: {}", employeeId, from, to);
        
        // Validate employee exists
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> {
                logger.error("Employee not found with ID: {}", employeeId);
                return new ResourceNotFoundException("Employee", "id", employeeId);
            });

        // Set default date range to last 7 days if not provided
        if (from == null) {
            from = LocalDateTime.now().minusDays(7).withHour(0).withMinute(0).withSecond(0);
            logger.debug("Using default from date: {}", from);
        }
        if (to == null) {
            to = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
            logger.debug("Using default to date: {}", to);
        }

        List<Attendance> attendances = attendanceRepository.findByEmployeeIdAndDateRange(employeeId, from, to);
        logger.debug("Found {} attendance records", attendances.size());
        
        Map<LocalDate, List<Attendance>> dailyAttendances = attendances.stream()
            .collect(Collectors.groupingBy(Attendance::getDate));

        List<AttendanceSummaryResponse.DailyAttendanceSummary> dailySummaries = new ArrayList<>();
        double totalHours = 0;
        double totalOvertimeHours = 0;
        int totalWorkingDays = 0;
        int totalHolidays = 0;
        int totalWeekends = 0;

        for (Map.Entry<LocalDate, List<Attendance>> entry : dailyAttendances.entrySet()) {
            List<Attendance> dayAttendances = entry.getValue();
            Attendance firstAttendance = dayAttendances.get(0);
            
            double dayHours = calculateDayHours(dayAttendances);
            double overtimeHours = calculateOvertimeHours(dayAttendances);
            
            if (firstAttendance.isWorkingDay()) {
                totalWorkingDays++;
            }
            if (firstAttendance.isHoliday()) {
                totalHolidays++;
            }
            if (firstAttendance.isWeekend()) {
                totalWeekends++;
            }
            
            totalHours += dayHours;
            totalOvertimeHours += overtimeHours;

            logger.debug("Day: {}, Hours: {}, Overtime: {}", entry.getKey(), dayHours, overtimeHours);

            AttendanceSummaryResponse.DailyAttendanceSummary dailySummary = AttendanceSummaryResponse.DailyAttendanceSummary.builder()
                .date(entry.getKey())
                .attendances(dayAttendances.stream()
                    .map(this::convertToAttendanceResponse)
                    .collect(Collectors.toList()))
                .isWorkingDay(firstAttendance.isWorkingDay())
                .isHoliday(firstAttendance.isHoliday())
                .isWeekend(firstAttendance.isWeekend())
                .totalHours(dayHours)
                .overtimeHours(overtimeHours)
                .holidayName(firstAttendance.getHolidayName())
                .build();

            dailySummaries.add(dailySummary);
        }

        logger.info("Summary - Total Hours: {}, Overtime: {}, Working Days: {}, Holidays: {}, Weekends: {}", 
            totalHours, totalOvertimeHours, totalWorkingDays, totalHolidays, totalWeekends);

        AttendanceSummaryResponse.DurationSummary totalSummary = AttendanceSummaryResponse.DurationSummary.builder()
            .totalHours(totalHours)
            .totalOvertimeHours(totalOvertimeHours)
            .totalWorkingDays(totalWorkingDays)
            .totalHolidays(totalHolidays)
            .totalWeekends(totalWeekends)
            .build();

        return AttendanceSummaryResponse.builder()
            .dailySummaries(dailySummaries)
            .totalSummary(totalSummary)
            .employeeId(employee.getId())
            .employeeName(employee.getName())
            .fromDate(from.toLocalDate())
            .toDate(to.toLocalDate())
            .build();
    }

    private AttendanceResponse mapToResponse(Attendance attendance) {
        return AttendanceResponse.builder()
                .id(attendance.getId())
                .employeeId(attendance.getEmployee().getId())
                .employeeName(attendance.getEmployee().getName())
                .timestamp(attendance.getTimestamp())
                .date(attendance.getDate())
                .time(attendance.getTime())
                .action(attendance.getAction().name())
                .durationMinutes(attendance.getDurationMinutes() != null ? attendance.getDurationMinutes().intValue() : null)
                .isWorkingDay(attendance.isWorkingDay())
                .isHoliday(attendance.isHoliday())
                .holidayName(attendance.getHolidayName())
                .isWeekend(attendance.isWeekend())
                .isOvertime(attendance.isOvertime())
                .remarks(attendance.getRemarks())
                .build();
    }

    private AttendanceResponse convertToAttendanceResponse(Attendance attendance) {
        return AttendanceResponse.builder()
            .id(attendance.getId())
            .employeeId(attendance.getEmployee().getId())
            .employeeName(attendance.getEmployee().getName())
            .timestamp(attendance.getTimestamp())
            .action(attendance.getAction().name())
            .build();
    }

    private double calculateDayHours(List<Attendance> dayAttendances) {
        double totalHours = 0;
        LocalDateTime lastPunchIn = null;

        for (Attendance attendance : dayAttendances) {
            if (AttendanceEvent.PUNCH_IN.equals(attendance.getAction())) {
                lastPunchIn = attendance.getTimestamp();
            } else if (AttendanceEvent.PUNCH_OUT.equals(attendance.getAction()) && lastPunchIn != null) {
                Duration duration = Duration.between(lastPunchIn, attendance.getTimestamp());
                totalHours += duration.toMinutes() / 60.0;
                lastPunchIn = null;
            }
        }

        return totalHours;
    }

    private double calculateOvertimeHours(List<Attendance> dayAttendances) {
        double overtimeHours = 0;
        LocalDateTime lastPunchIn = null;

        for (Attendance attendance : dayAttendances) {
            if (AttendanceEvent.PUNCH_IN.equals(attendance.getAction())) {
                lastPunchIn = attendance.getTimestamp();
            } else if (AttendanceEvent.PUNCH_OUT.equals(attendance.getAction()) && lastPunchIn != null) {
                if (attendance.isOvertime()) {
                    Duration duration = Duration.between(lastPunchIn, attendance.getTimestamp());
                    overtimeHours += duration.toMinutes() / 60.0;
                }
                lastPunchIn = null;
            }
        }

        return overtimeHours;
    }
} 