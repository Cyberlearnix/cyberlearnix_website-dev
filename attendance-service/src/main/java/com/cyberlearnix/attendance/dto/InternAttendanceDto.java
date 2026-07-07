package com.cyberlearnix.attendance.dto;

import com.cyberlearnix.attendance.entity.MeetingAttendance;
import lombok.Data;

import java.time.LocalDateTime;

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

    public static InternAttendanceDto from(MeetingAttendance fa, String meetingTitle, String courseId) {
        InternAttendanceDto dto = new InternAttendanceDto();
        dto.setId(fa.getId());
        dto.setMeetingId(fa.getMeetingId());
        dto.setMeetingTitle(meetingTitle);
        dto.setCourseId(courseId);
        dto.setStudentId(fa.getStudentId());
        dto.setStudentName(fa.getStudentId());
        dto.setStudentEmail(fa.getStudentId());
        dto.setTotalActiveSeconds(fa.getDurationMinutes() != null ? (long) fa.getDurationMinutes() * 60 : 0L);
        dto.setMeetingDurationSeconds(3600L);
        dto.setAttendancePercentage(fa.getAttendancePercentage());
        dto.setStatus(fa.getAttendanceStatus() != null ? fa.getAttendanceStatus().name() : "ABSENT");
        dto.setRejoinCount(0);
        dto.setLate(fa.getAttendanceStatus() == MeetingAttendance.AttendanceStatus.LATE);
        dto.setLateByMinutes(0);
        dto.setOverridden(false);
        dto.setLocked(false);
        dto.setCreatedAt(fa.getCreatedAt());
        dto.setUpdatedAt(fa.getCreatedAt());
        return dto;
    }
}
