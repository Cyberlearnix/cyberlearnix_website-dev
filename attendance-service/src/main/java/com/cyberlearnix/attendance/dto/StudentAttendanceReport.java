package com.cyberlearnix.attendance.dto;

import lombok.Data;

import java.util.List;

@Data
public class StudentAttendanceReport {
    private String studentId;
    private String studentName;
    private String studentEmail;
    private String courseId;
    private String batchId;

    private Integer totalMeetings;
    private Integer presentCount;
    private Integer partialCount;
    private Integer lateCount;
    private Integer absentCount;
    private Integer excusedCount;
    private Double overallPercentage;

    private Boolean certificateEligible;
    private String ineligibilityReason;

    private List<AttendanceDto> sessions;
}
