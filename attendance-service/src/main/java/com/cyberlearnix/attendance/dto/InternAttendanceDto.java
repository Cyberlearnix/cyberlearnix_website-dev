package com.cyberlearnix.attendance.dto;

import com.cyberlearnix.attendance.entity.FinalAttendance;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Enriched attendance record for the intern attendance admin panel.
 * Combines FinalAttendance data with meeting info (title, courseId).
 */
@Data
public class InternAttendanceDto {

    private String id;
    private String meetingId;
    private String meetingTitle;
    private String courseId;
    private String studentId;
    private String studentName;
    private String studentEmail;
    private Long totalActiveSeconds;
    private Long meetingDurationSeconds;
    private Double attendancePercentage;
    private String status;
    private Integer rejoinCount;
    private Boolean late;
    private Integer lateByMinutes;
    private Boolean overridden;
    private String overrideBy;
    private LocalDateTime overrideAt;
    private String overrideReason;
    private Boolean locked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InternAttendanceDto from(FinalAttendance fa, String meetingTitle, String courseId) {
        InternAttendanceDto dto = new InternAttendanceDto();
        dto.setId(fa.getId());
        dto.setMeetingId(fa.getMeetingId());
        dto.setMeetingTitle(meetingTitle);
        dto.setCourseId(courseId);
        dto.setStudentId(fa.getStudentId());
        dto.setStudentName(fa.getStudentName());
        dto.setStudentEmail(fa.getStudentEmail());
        dto.setTotalActiveSeconds(fa.getTotalActiveSeconds());
        dto.setMeetingDurationSeconds(fa.getMeetingDurationSeconds());
        dto.setAttendancePercentage(fa.getAttendancePercentage());
        dto.setStatus(fa.getStatus() != null ? fa.getStatus().name() : "ABSENT");
        dto.setRejoinCount(fa.getRejoinCount());
        dto.setLate(fa.getLate());
        dto.setLateByMinutes(fa.getLateByMinutes());
        dto.setOverridden(fa.getOverridden());
        dto.setOverrideBy(fa.getOverrideBy());
        dto.setOverrideAt(fa.getOverrideAt());
        dto.setOverrideReason(fa.getOverrideReason());
        dto.setLocked(fa.getLocked());
        dto.setCreatedAt(fa.getCreatedAt());
        dto.setUpdatedAt(fa.getUpdatedAt());
        return dto;
    }
}
