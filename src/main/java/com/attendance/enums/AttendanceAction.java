package com.attendance.enums;

public enum AttendanceAction {
    PUNCH_IN,
    PUNCH_OUT;

    public static boolean isValid(String action) {
        try {
            valueOf(action);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
} 