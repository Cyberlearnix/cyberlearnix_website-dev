package com.cyberlearnix.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "meetings", indexes = {
    @Index(name = "idx_meeting_zoho_id", columnList = "zohoMeetingId"),
    @Index(name = "idx_meeting_course_id", columnList = "courseId"),
    @Index(name = "idx_meeting_batch_id", columnList = "batchId"),
    @Index(name = "idx_meeting_scheduled_start", columnList = "scheduledStart"),
    @Index(name = "idx_meeting_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "zoho_meeting_id", unique = true)
    private String zohoMeetingId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalDateTime scheduledEnd;

    @Column(name = "actual_start")
    private LocalDateTime actualStart;

    @Column(name = "actual_end")
    private LocalDateTime actualEnd;

    /** Duration in minutes — computed when meeting ends */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "host_user_id", nullable = false)
    private String hostUserId;

    @Column(name = "host_name")
    private String hostName;

    @Column(name = "course_id")
    private String courseId;

    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "meeting_url")
    private String meetingUrl;

    @Column(name = "meeting_password")
    private String meetingPassword;

    @Column(name = "zoho_join_url")
    private String zohoJoinUrl;

    /** SCHEDULED | LIVE | ENDED | CANCELLED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    /** Whether attendance has been finalised (after meeting ends) */
    @Column(name = "attendance_finalized")
    private Boolean attendanceFinalized = false;

    @Column(name = "is_mandatory")
    private Boolean mandatory = true;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum MeetingStatus {
        SCHEDULED, LIVE, ENDED, CANCELLED
    }
}
