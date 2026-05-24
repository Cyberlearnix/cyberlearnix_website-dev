package com.cyberlearnix.attendance.dto;

import com.cyberlearnix.attendance.entity.Meeting;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MeetingDto {
    private String id;
    private String zohoMeetingId;
    private String title;
    private String description;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;
    private Integer durationMinutes;
    private String hostUserId;
    private String hostName;
    private String courseId;
    private String batchId;
    private String meetingUrl;
    private String zohoJoinUrl;
    private Meeting.MeetingStatus status;
    private Boolean attendanceFinalized;
    private Boolean mandatory;
    private String notes;
    private Long liveParticipantCount;
    private LocalDateTime createdAt;
}
