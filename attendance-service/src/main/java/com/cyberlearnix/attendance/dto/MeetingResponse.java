package com.cyberlearnix.attendance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MeetingResponse {

    private String id;
    private String title;
    private String description;
    private String meetingCode;
    private Long courseId;
    private String facultyId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String createdBy;
    private String joinUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getSubject() {
        return title;
    }

    public LocalDateTime getStartDateTime() {
        return startTime;
    }

    public LocalDateTime getEndDateTime() {
        return endTime;
    }

    public String getZohoJoinUrl() {
        return joinUrl;
    }
}
