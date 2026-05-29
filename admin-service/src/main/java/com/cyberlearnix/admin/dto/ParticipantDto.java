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
}