package com.cyberlearnix.attendance.dto;

import lombok.Data;

import java.util.List;

@Data
public class AttendanceAnalyticsDto {

    private String courseId;
    private String batchId;

    // Overall stats
    private Integer totalMeetings;
    private Integer totalStudents;
    private Double avgAttendancePercentage;

    // Status distribution
    private Long presentCount;
    private Long partialCount;
    private Long lateCount;
    private Long absentCount;
    private Long excusedCount;

    // Trend data (date → avg percentage)
    private List<TrendPoint> dailyTrend;
    private List<TrendPoint> weeklyTrend;

    // Top/bottom performers
    private List<StudentRank> topPerformers;
    private List<StudentRank> atRiskStudents;

    // Meeting-wise breakdown
    private List<MeetingBreakdown> meetingBreakdown;

    @Data
    public static class TrendPoint {
        private String date;
        private Double avgPercentage;
        private Integer totalStudents;
        private Integer presentCount;
    }

    @Data
    public static class StudentRank {
        private String studentId;
        private String studentName;
        private String studentEmail;
        private Double avgPercentage;
        private Integer attendedCount;
        private Integer totalMeetings;
        private String eligibilityStatus;
    }

    @Data
    public static class MeetingBreakdown {
        private String meetingId;
        private String meetingTitle;
        private String scheduledStart;
        private Integer totalInvited;
        private Integer totalJoined;
        private Double avgAttendancePercent;
        private Long presentCount;
        private Long absentCount;
    }
}
