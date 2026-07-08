package com.cyberlearnix.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateMeetingRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private Long courseId;

    // Set programmatically from X-User-Id header; never validated in request body
    private String facultyId;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    private String createdBy;

    public void setSubject(String subject) {
        this.title = subject;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startTime = startDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endTime = endDateTime;
    }
}
