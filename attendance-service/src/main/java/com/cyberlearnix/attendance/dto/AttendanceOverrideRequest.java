package com.cyberlearnix.attendance.dto;

import com.cyberlearnix.attendance.entity.FinalAttendance;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AttendanceOverrideRequest {

    @NotBlank
    private String meetingId;

    @NotBlank
    private String studentId;

    private FinalAttendance.AttendanceStatus newStatus;

    /** Override active seconds (for duration edit) */
    private Long newActiveSeconds;

    /** Override percentage directly */
    private Double newPercentage;

    @NotBlank
    private String action; // MARK_PRESENT | MARK_ABSENT | MARK_EXCUSED | EDIT_DURATION | LOCK | UNLOCK

    private String reason;
    private String notes;
    private Boolean lock;
}
