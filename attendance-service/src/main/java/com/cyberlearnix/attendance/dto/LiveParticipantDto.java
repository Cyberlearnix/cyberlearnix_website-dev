package com.cyberlearnix.attendance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiveParticipantDto {
    private String studentId;
    private String studentName;
    private String studentEmail;
    private LocalDateTime joinedAt;
    private Long currentDurationSeconds;
    private Integer rejoinCount;
    private String ipAddress;
    private String deviceInfo;
    private String browserInfo;
    private String status; // ACTIVE | RECONNECTING
    private LocalDateTime lastHeartbeat;
}
