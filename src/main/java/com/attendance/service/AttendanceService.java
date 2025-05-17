package com.attendance.service;

import com.attendance.dto.AttendanceResponse;
import com.attendance.dto.AttendanceSummaryResponse;
import com.attendance.entity.Attendance;
import com.attendance.entity.Employee;
import com.attendance.enums.AttendanceEvent;
import com.attendance.repository.AttendanceRepository;
import com.attendance.repository.EmployeeRepository;
import com.attendance.exception.ResourceNotFoundException;
import com.attendance.exception.InvalidAttendanceActionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceService {
    private static final Logger logger = LoggerFactory.getLogger(AttendanceService.class);
    private static final LocalTime WORK_START_TIME = LocalTime.of(9, 0); // 9:00 AM
    private static final LocalTime WORK_END_TIME = LocalTime.of(17, 0); // 5:00 PM

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Transactional
    public AttendanceResponse markAttendance(Long employeeId, AttendanceEvent requestedEvent) {
        logger.info("Marking attendance for employeeId: {} with event: {}", employeeId, requestedEvent);
        
        // Validate employee exists
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> {
                logger.error("Employee not found with ID: {}", employeeId);
                return new ResourceNotFoundException("Employee", "id", employeeId);
            });

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();
        logger.debug("Current time: {}, Date: {}, Time: {}", now, today, currentTime);

        // Get last attendance record
        Attendance lastAttendance = attendanceRepository.findLastAttendanceByEmployeeId(employeeId);
        logger.debug("Last attendance record: {}", lastAttendance);
        
        // Validate action sequence
        String message;
        if (lastAttendance == null) {
            if (requestedEvent != AttendanceEvent.PUNCH_IN) {
                logger.error("Invalid first attendance action: {}", requestedEvent);
                throw new InvalidAttendanceActionException("First attendance record must be PUNCH_IN");
            }
            message = AttendanceEvent.PUNCH_IN.getMessage();
            logger.info("First attendance record created for employee: {}", employeeId);
        } else if (AttendanceEvent.PUNCH_OUT.equals(lastAttendance.getAction())) {
            if (requestedEvent != AttendanceEvent.PUNCH_IN) {
                logger.error("Invalid attendance sequence after PUNCH_OUT: {}", requestedEvent);
                throw new InvalidAttendanceActionException("After PUNCH_OUT, only PUNCH_IN is allowed");
            }
            message = "Welcome back!";
            logger.info("Employee {} returned from break", employeeId);
        } else {
            if (requestedEvent != AttendanceEvent.PUNCH_OUT) {
                logger.error("Invalid attendance sequence after PUNCH_IN: {}", requestedEvent);
                throw new InvalidAttendanceActionException("After PUNCH_IN, only PUNCH_OUT is allowed");
            }
            message = AttendanceEvent.PUNCH_OUT.getMessage();
            logger.info("Employee {} completed work session", employeeId);
        }

        // Create and save new attendance record
        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setTimestamp(now);
        attendance.setAction(requestedEvent);
        attendance.setWorkingDay(!isWeekend(today) && !isHoliday(today));
        attendance.setWeekend(isWeekend(today));
        attendance.setHoliday(isHoliday(today));
        attendance.setOvertime(isOvertime(currentTime));
        
        if (requestedEvent == AttendanceEvent.PUNCH_OUT && lastAttendance != null) {
            Duration duration = Duration.between(lastAttendance.getTimestamp(), now);
            attendance.setDurationMinutes(duration.toMinutes());
            logger.debug("Session duration: {} minutes", duration.toMinutes());
        }
        
        Attendance savedAttendance = attendanceRepository.save(attendance);
        logger.info("Attendance record saved with ID: {}", savedAttendance.getId());

        // Build response
        return AttendanceResponse.builder()
                .id(savedAttendance.getId())
                .employeeId(employee.getId())
                .employeeName(employee.getName())
                .timestamp(savedAttendance.getTimestamp())
                .action(savedAttendance.getAction().name())
                .message(message)
                .build();
    }

    private boolean isWeekend(LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        return dayOfWeek >= 6; // Saturday = 6, Sunday = 7
    }

    private boolean isHoliday(LocalDate date) {
        // TODO: Implement holiday checking logic
        // This could be enhanced with a holiday calendar or external service
        return false;
    }

    private boolean isOvertime(LocalTime time) {
        return time.isAfter(WORK_END_TIME);
    }

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