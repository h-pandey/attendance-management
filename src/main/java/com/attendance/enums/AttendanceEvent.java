package com.attendance.enums;

import lombok.Getter;

@Getter
public enum AttendanceEvent {
    PUNCH_IN("Have a great day."),
    PUNCH_OUT("Thank you for your work.");

    private final String message;

    AttendanceEvent(String message) {
        this.message = message;
    }
} 