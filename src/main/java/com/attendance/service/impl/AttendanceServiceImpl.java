package com.attendance.service.impl;

import com.attendance.dto.AttendanceResponse;
import com.attendance.dto.AttendanceSummaryResponse;
import com.attendance.entity.Attendance;
import com.attendance.entity.Employee;
import com.attendance.enums.AttendanceAction;
import com.attendance.enums.AttendanceEvent;
import com.attendance.exception.InvalidAttendanceException;
import com.attendance.exception.ResourceNotFoundException;
import com.attendance.repository.AttendanceRepository;
import com.attendance.repository.EmployeeRepository;
import com.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private static final LocalTime WORK_START_TIME = LocalTime.of(9, 0); // 9:00 AM
    private static final LocalTime WORK_END_TIME = LocalTime.of(17, 0); // 5:00 PM

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional
    public AttendanceResponse markAttendance(Long employeeId, String event, String remarks) {
        
        // Validate event type using enum
        if (!AttendanceAction.isValid(event)) {
            throw new IllegalArgumentException("Invalid event type. Must be either PUNCH_IN or PUNCH_OUT");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        LocalDateTime timestamp = LocalDateTime.now();
        LocalDate date = timestamp.toLocalDate();
        LocalTime time = timestamp.toLocalTime();

        // Validate action type using enum
        AttendanceAction action = AttendanceAction.valueOf(event);
        boolean isPunchIn = action == AttendanceAction.PUNCH_IN;
        boolean isPunchOut = action == AttendanceAction.PUNCH_OUT;

        // Get all attendance entries for the day, sorted by timestamp
        List<Attendance> existingEntries = attendanceRepository.findByEmployeeIdAndDate(employeeId, date)
                .stream()
                .sorted(Comparator.comparing(Attendance::getTimestamp))
                .collect(Collectors.toList());

        // Validate punch in/out sequence
        if (!existingEntries.isEmpty()) {
            Attendance lastEntry = existingEntries.get(existingEntries.size() - 1);
            String lastAction = lastEntry.getAction().name();
            
            if (isPunchIn && AttendanceAction.PUNCH_IN.name().equals(lastAction)) {
                throw new InvalidAttendanceException("Cannot punch in twice in a row. Last action was also PUNCH_IN");
            }
            if (isPunchOut && AttendanceAction.PUNCH_OUT.name().equals(lastAction)) {
                throw new InvalidAttendanceException("Cannot punch out twice in a row. Last action was also PUNCH_OUT");
            }
        } else if (isPunchOut) {
            throw new InvalidAttendanceException("Cannot punch out without a previous punch-in record");
        }

        // Check if it's a weekend
        boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;

        // Check if it's a holiday (you can implement your own holiday checking logic)
        boolean isHoliday = false; // Implement holiday checking logic
        String holidayName = null; // Set holiday name if it's a holiday

        // Check if it's overtime (after 6 PM)
        boolean isOvertime = time.isAfter(LocalTime.of(18, 0));

        // Calculate duration if it's a punch-out
        Long durationMinutes = null;
        if (isPunchOut && !existingEntries.isEmpty()) {
            // Find the last punch-in
            Optional<Attendance> lastPunchIn = existingEntries.stream()
                    .filter(entry -> AttendanceAction.PUNCH_IN.name().equals(entry.getAction().name()))
                    .reduce((first, second) -> second); // Get the last punch-in

            if (lastPunchIn.isPresent()) {
                durationMinutes = Duration.between(lastPunchIn.get().getTimestamp(), timestamp).toMinutes();
            }
        }

        Attendance attendance = Attendance.builder()
                .employee(employee)
                .timestamp(timestamp)
                .date(date)
                .time(time)
                .action(AttendanceEvent.valueOf(event))
                .durationMinutes(durationMinutes)
                .isWorkingDay(!isWeekend && !isHoliday)
                .isHoliday(isHoliday)
                .holidayName(holidayName)
                .isWeekend(isWeekend)
                .isOvertime(isOvertime)
                .remarks(remarks)
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
    public AttendanceSummaryResponse getAttendanceForDuration(Long employeeId, LocalDate fromDate, LocalDate toDate) {
        log.info("Fetching attendance summary for employeeId: {} from: {} to: {}", employeeId, fromDate, toDate);
        
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(23, 59, 59) : null;

        // Validate employee exists
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> {
                log.error("Employee not found with ID: {}", employeeId);
                return new ResourceNotFoundException("Employee", "id", employeeId);
            });

        // Set default date range to last 7 days if not provided
        if (from == null) {
            from = LocalDateTime.now().minusDays(7).withHour(0).withMinute(0).withSecond(0);
            log.debug("Using default from date: {}", from);
        }
        if (to == null) {
            to = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
            log.debug("Using default to date: {}", to);
        }

        List<Attendance> attendances = attendanceRepository.findByEmployeeIdAndDateRange(employeeId, from, to);
        log.debug("Found {} attendance records", attendances.size());
        
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

            log.debug("Day: {}, Hours: {}, Overtime: {}", entry.getKey(), dayHours, overtimeHours);

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

        log.info("Summary - Total Hours: {}, Overtime: {}, Working Days: {}, Holidays: {}, Weekends: {}", 
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