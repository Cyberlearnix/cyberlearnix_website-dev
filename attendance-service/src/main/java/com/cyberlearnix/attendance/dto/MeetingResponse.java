package com.cyberlearnix.attendance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MeetingResponse {

    private String id;
    // Serialized as both "title" and "subject" so the frontend can read either
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
    // Empty list by default — populated by attendance report endpoints
    private List<Object> invitees = List.of();

    /** Alias for title — used by the admin frontend */
    @JsonProperty("subject")
    public String getSubject() {
        return title;
    }

    /** Alias for startTime — used by the admin frontend */
    @JsonProperty("startDateTime")
    public LocalDateTime getStartDateTime() {
        return startTime;
    }

    /** Alias for endTime — used by the admin frontend */
    @JsonProperty("endDateTime")
    public LocalDateTime getEndDateTime() {
        return endTime;
    }
}
