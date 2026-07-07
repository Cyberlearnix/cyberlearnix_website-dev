package com.cyberlearnix.attendance.dto;

import lombok.Data;

import java.util.List;

@Data
public class MeetingReportResponse {

    private String meetingId;
    private String title;
    private Long courseId;
    private String facultyId;
    private Integer totalParticipants;
    private Double averageAttendancePercentage;
    private List<AttendanceResponse> studentAttendanceList;
}
