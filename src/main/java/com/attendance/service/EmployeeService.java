package com.attendance.service;

import com.attendance.dto.EmployeeRequest;
import com.attendance.dto.EmployeeResponse;
import com.attendance.entity.Employee;
import com.attendance.exception.DuplicateEmailException;
import com.attendance.repository.EmployeeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class EmployeeService {
    @Autowired
    private EmployeeRepository employeeRepository;

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        log.info("Creating new employee with email: {}", request.getEmail());
        
        // Check for duplicate email
        if (employeeRepository.existsByEmail(request.getEmail())) {
            log.error("Duplicate email found: {}", request.getEmail());
            throw new DuplicateEmailException(request.getEmail());
        }

        Employee employee = new Employee();
        employee.setName(request.getName());
        employee.setEmail(request.getEmail());
        employee.setDepartment(request.getDepartment());

        Employee savedEmployee = employeeRepository.save(employee);
        log.info("Employee created successfully with ID: {}", savedEmployee.getId());

        return EmployeeResponse.builder()
                .id(savedEmployee.getId())
                .name(savedEmployee.getName())
                .email(savedEmployee.getEmail())
                .department(savedEmployee.getDepartment())
                .build();
    }
} 