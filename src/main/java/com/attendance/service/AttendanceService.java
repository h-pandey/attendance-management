package com.attendance.service;

import java.time.LocalDate;
import java.util.List;

import com.attendance.dto.AttendanceResponse;
import com.attendance.dto.AttendanceSummaryResponse;

public interface AttendanceService {
    AttendanceResponse markAttendance(Long employeeId, String event, String remarks);
    
    List<AttendanceResponse> getAttendanceByEmployeeId(Long employeeId);
    
    AttendanceSummaryResponse getAttendanceForDuration(Long employeeId, LocalDate fromDate, LocalDate toDate);
} 