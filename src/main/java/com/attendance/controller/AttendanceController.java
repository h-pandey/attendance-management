package com.attendance.controller;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.dto.AttendanceResponse;
import com.attendance.dto.AttendanceSummaryResponse;
import com.attendance.service.AttendanceService;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/{employeeId}/mark/{event}")
    public ResponseEntity<AttendanceResponse> markAttendance(
            @PathVariable Long employeeId,
            @PathVariable String event,
            @RequestParam(required = false) String remarks) {

        AttendanceResponse response = attendanceService.markAttendance(employeeId, event, remarks);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{employeeId}/summary")
    public ResponseEntity<AttendanceSummaryResponse> getAttendanceSummary(
            @PathVariable Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate toDate) {
        
        return ResponseEntity.ok(attendanceService.getAttendanceForDuration(employeeId, fromDate, toDate));
    }
} 