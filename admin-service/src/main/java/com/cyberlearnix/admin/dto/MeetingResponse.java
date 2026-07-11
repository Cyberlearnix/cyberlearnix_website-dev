package com.cyberlearnix.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private String joinUrl;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // Empty by default — populated by the frontend from the attendance report endpoint
    private List<Map<String, String>> invitees = List.of();

    // ── Aliases used by the admin frontend ────────────────────────────────────
    /** Alias: frontend reads meeting.subject */
    public String getSubject() { return title; }

    /** Alias: frontend reads meeting.startDateTime */
    public LocalDateTime getStartDateTime() { return startTime; }

    /** Alias: frontend reads meeting.endDateTime */
    public LocalDateTime getEndDateTime() { return endTime; }

    /** Alias: frontend reads meeting.meetingId */
    public String getMeetingId() { return meetingCode; }
}
