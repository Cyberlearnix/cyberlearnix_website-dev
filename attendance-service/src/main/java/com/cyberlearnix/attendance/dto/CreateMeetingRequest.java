package com.cyberlearnix.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateMeetingRequest {
    @NotBlank
    private String title;

    private String description;

    @NotNull
    private LocalDateTime scheduledStart;

    @NotNull
    private LocalDateTime scheduledEnd;

    private String courseId;
    private String batchId;

    private Boolean mandatory = true;
    private String notes;

    /** If true, also create the meeting in Zoho */
    private Boolean createInZoho = false;
}
