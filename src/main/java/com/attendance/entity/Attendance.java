package com.attendance.entity;

import com.attendance.enums.AttendanceEvent;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Entity
@Table(name = "attendance", indexes = {
    @Index(name = "idx_employee_date", columnList = "employee_id, date"),
    @Index(name = "idx_employee_timestamp", columnList = "employee_id, timestamp"),
    @Index(name = "idx_date", columnList = "date")
})
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    
    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = false)
    private LocalDate date;
    
    @Column(nullable = false)
    private LocalTime time;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceEvent action;
    
    @Column(name = "duration_minutes")
    private Long durationMinutes;
    
    @Column(name = "is_working_day", nullable = false)
    private boolean isWorkingDay;
    
    @Column(name = "is_holiday")
    private boolean isHoliday;
    
    @Column(name = "holiday_name")
    private String holidayName;
    
    @Column(name = "is_weekend")
    private boolean isWeekend;
    
    @Column(name = "is_overtime")
    private boolean isOvertime;
    
    @Column(length = 500)
    private String remarks;
    
    @PrePersist
    public void prePersist() {
        if (timestamp != null) {
            date = timestamp.toLocalDate();
            time = timestamp.toLocalTime();
            isWeekend = date.getDayOfWeek().getValue() >= 6; // Saturday = 6, Sunday = 7
        }
    }
} 