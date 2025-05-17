package com.attendance.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AttendanceRequest {
    private LocalDateTime timestamp;
    private String action;
    private String remarks;
} 