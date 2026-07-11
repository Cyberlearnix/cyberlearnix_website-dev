package com.cyberlearnix.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class CreateMeetingRequest {

    @NotBlank(message = "Meeting subject is required")
    private String subject;

    private String description;

    // startDateTime and endDateTime sent by the frontend
    @NotNull(message = "Start date/time is required")
    private LocalDateTime startDateTime;

    @NotNull(message = "End date/time is required")
    private LocalDateTime endDateTime;

    private Long courseId;

    // Set from X-User-Id header in the controller — never required in the request body
    private String facultyId;

    // Participant list e.g. [{"name":"Alice","email":"alice@example.com"}]
    private List<Map<String, String>> invitees;
}
