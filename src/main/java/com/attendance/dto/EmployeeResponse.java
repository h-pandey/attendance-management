package com.attendance.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class EmployeeResponse {
    private Long id;
    private String name;
    private String email;
    private String department;
} 