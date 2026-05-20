package com.cyberlearnix.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TeamsMeetingRequest {

    @NotBlank(message = "Meeting subject is required")
    private String subject;

    @NotNull(message = "Start date/time is required")
    @Future(message = "Start date/time must be in the future")
    private LocalDateTime startDateTime;

    @NotNull(message = "End date/time is required")
    private LocalDateTime endDateTime;

    /** Optional description or agenda visible to participants */
    private String description;

    private Long courseId;
    private String batchId;

    /**
     * Optional list of participants to invite. Zoho sends each invitee an
     * email with a personalised join link (panelist role).
     */
    @Valid
    private List<PanelistDto> invitees;

    // ── Recurring meeting fields ──────────────────────────────────────

    /** Set to true to enable recurring meeting. */
    private boolean recurring = false;

    /**
     * Recurrence type. Required when recurring=true.
     * Values: DAILY, WEEKLY, MONTHLY
     */
    private String repeatType;

    /**
     * Repeat interval. E.g. 1 = every day/week/month, 2 = every 2 days etc.
     * Defaults to 1 if not provided.
     */
    @Min(value = 1, message = "Repeat every must be at least 1")
    private int repeatEvery = 1;

    /**
     * End date for the recurrence series (inclusive). Required when recurring=true.
     * Format: yyyy-MM-dd
     */
    private LocalDate recurrenceEndDate;
}
