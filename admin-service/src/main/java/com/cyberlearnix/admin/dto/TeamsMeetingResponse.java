package com.cyberlearnix.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TeamsMeetingResponse {

    private Long id;
    private String subject;
    private String description;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;

    /** Zoho meeting key shown as Meeting ID to participants */
    private String meetingId;

    /** URL users click to join the meeting */
    private String joinUrl;

    /** Meeting password shown in the Zoho invitation */
    private String password;

    /** Human-readable meeting duration e.g. "1 hr", "1 hr 30 min" */
    private String duration;

    private String status;
    private String createdBy;
    private LocalDateTime createdAt;

    /** Participants invited to this meeting */
    private List<PanelistDto> invitees;

    // ── Recurring fields ──────────────────────────────────────────────────────

    private boolean recurring;
    private String repeatType;
    private Integer repeatEvery;
    private LocalDate recurrenceEndDate;
}
