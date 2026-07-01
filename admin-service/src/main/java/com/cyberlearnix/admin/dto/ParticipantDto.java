package com.cyberlearnix.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDto {
    private Long id;
    private String name;
    private String email;
    private LocalDateTime joinTime;
    private LocalDateTime leaveTime;
    private Long durationSeconds;

    /** Human-readable duration e.g. "45 min", "1 hr 10 min" — for UI table display */
    private String durationFormatted;

    /** "presenter" or "attendee" as returned by Zoho participant report */
    private String role;

    /** Join source: "web", "mobile", "phone", etc. */
    private String source;

    /** Human-readable in/out window from Zoho e.g. "02:20 PM - 02:21 PM" */
    private String inAndOutTime;

    /** Zoho member ZUID */
    private String memberId;
}