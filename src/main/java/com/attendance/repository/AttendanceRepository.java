package com.attendance.repository;

import com.attendance.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    
    @Query("SELECT a FROM Attendance a WHERE a.employee.id = :employeeId " +
           "AND a.timestamp BETWEEN :from AND :to ORDER BY a.timestamp")
    List<Attendance> findByEmployeeIdAndDateRange(
        @Param("employeeId") Long employeeId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
    
    @Query("SELECT a FROM Attendance a WHERE a.employee.id = :employeeId " +
           "ORDER BY a.timestamp DESC LIMIT 1")
    Attendance findLastAttendanceByEmployeeId(@Param("employeeId") Long employeeId);

    List<Attendance> findByEmployeeId(Long employeeId);
    List<Attendance> findByEmployeeIdAndDate(Long employeeId, LocalDate date);
} 