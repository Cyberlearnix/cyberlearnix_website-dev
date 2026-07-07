package com.cyberlearnix.attendance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttendanceResponse {

    private String id;
    private String meetingId;
    private String studentId;
    private LocalDateTime joinTime;
    private LocalDateTime leaveTime;
    private Integer durationMinutes;
    private Double attendancePercentage;
    private String attendanceStatus;
    private LocalDateTime createdAt;
}
