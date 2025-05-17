package com.attendance.service;

import com.attendance.dto.EmployeeRequest;
import com.attendance.dto.EmployeeResponse;
import com.attendance.entity.Employee;
import com.attendance.exception.DuplicateEmailException;
import com.attendance.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {
    private static final Logger logger = LoggerFactory.getLogger(EmployeeService.class);

    @Autowired
    private EmployeeRepository employeeRepository;

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        logger.info("Creating new employee with email: {}", request.getEmail());
        
        // Check for duplicate email
        if (employeeRepository.existsByEmail(request.getEmail())) {
            logger.error("Duplicate email found: {}", request.getEmail());
            throw new DuplicateEmailException(request.getEmail());
        }

        Employee employee = new Employee();
        employee.setName(request.getName());
        employee.setEmail(request.getEmail());
        employee.setDepartment(request.getDepartment());

        Employee savedEmployee = employeeRepository.save(employee);
        logger.info("Employee created successfully with ID: {}", savedEmployee.getId());

        return EmployeeResponse.builder()
                .id(savedEmployee.getId())
                .name(savedEmployee.getName())
                .email(savedEmployee.getEmail())
                .department(savedEmployee.getDepartment())
                .build();
    }
} 