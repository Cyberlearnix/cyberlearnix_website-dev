package com.cyberlearnix.attendance.dto;

import com.cyberlearnix.attendance.entity.MeetingAttendance;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttendanceDto {
    private String id;
    private String meetingId;
    private String meetingTitle;
    private LocalDateTime meetingScheduledStart;
    private String studentId;
    private String studentName;
    private String studentEmail;
    private Long totalActiveSeconds;
    private Long meetingDurationSeconds;
    private Double attendancePercentage;
    private MeetingAttendance.AttendanceStatus status;
    private Integer rejoinCount;
    private Boolean late;
    private Integer lateByMinutes;
    private Boolean overridden;
    private String overrideBy;
    private LocalDateTime overrideAt;
    private String overrideReason;
    private Boolean locked;
    private Boolean countsForCertificate;
    private String adminNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
