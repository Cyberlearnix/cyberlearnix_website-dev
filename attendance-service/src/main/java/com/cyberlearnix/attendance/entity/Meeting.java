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
    @Index(name = "idx_meeting_code", columnList = "meeting_code", unique = true),
    @Index(name = "idx_meeting_course", columnList = "course_id"),
    @Index(name = "idx_meeting_faculty", columnList = "faculty_id")
})
@EntityListeners(AuditingEntityListener.class)
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // unique = true removed here — the named unique index above already enforces uniqueness
    @Column(name = "meeting_code", nullable = false)
    private String meetingCode;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "faculty_id", nullable = false)
    private String facultyId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Column(name = "created_by")
    private String createdBy;

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
