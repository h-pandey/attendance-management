package com.attendance.service;

import com.attendance.dto.AttendanceResponse;
import com.attendance.dto.AttendanceSummaryResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface AttendanceService {
    AttendanceResponse markAttendance(Long employeeId, String event, String remarks);
    
    List<AttendanceResponse> getAttendanceByEmployeeId(Long employeeId);
    
    AttendanceSummaryResponse getAttendanceForDuration(Long employeeId, LocalDateTime from, LocalDateTime to);
} 