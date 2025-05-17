package com.attendance.controller;

import com.attendance.dto.AttendanceRequest;
import com.attendance.dto.AttendanceResponse;
import com.attendance.dto.AttendanceSummaryResponse;
import com.attendance.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @PostMapping("/{employeeId}/mark/{event}")
    public ResponseEntity<AttendanceResponse> markAttendance(
            @PathVariable Long employeeId,
            @PathVariable String event) {
        AttendanceRequest request = new AttendanceRequest();
        request.setTimestamp(LocalDateTime.now());
        request.setAction(event);
        request.setRemarks(null);
        
        AttendanceResponse response = attendanceService.markAttendance(employeeId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{employeeId}/summary")
    public ResponseEntity<AttendanceSummaryResponse> getAttendanceSummary(
            @PathVariable Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate toDate) {
        
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(23, 59, 59) : null;
        
        return ResponseEntity.ok(attendanceService.getAttendanceForDuration(employeeId, from, to));
    }
} 