package com.attendance.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class DateRangeRequest {
    @DateTimeFormat(pattern = "yyyyMMdd")
    private LocalDate fromDate;

    @DateTimeFormat(pattern = "yyyyMMdd")
    private LocalDate toDate;
} 