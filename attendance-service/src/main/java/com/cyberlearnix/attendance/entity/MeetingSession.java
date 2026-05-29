package com.cyberlearnix.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Tracks a single join→leave cycle for a participant.
 * A student may have multiple sessions per meeting (rejoin support).
 */
@Data
@Entity
@Table(name = "meeting_sessions", indexes = {
    @Index(name = "idx_ms_meeting_id", columnList = "meetingId"),
    @Index(name = "idx_ms_student_id", columnList = "studentId"),
    @Index(name = "idx_ms_meeting_student", columnList = "meetingId,studentId")
})
@EntityListeners(AuditingEntityListener.class)
public class MeetingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "meeting_id", nullable = false)
    private String meetingId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "student_email")
    private String studentEmail;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    /** Duration of this specific join session in seconds */
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    /** Which rejoin sequence this is (1 = first join, 2 = first rejoin, ...) */
    @Column(name = "session_sequence")
    private Integer sessionSequence = 1;

    /** ACTIVE | COMPLETED | DISCONNECTED */
    @Enumerated(EnumType.STRING)
    @Column(name = "session_status")
    private SessionStatus sessionStatus = SessionStatus.ACTIVE;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "browser_info")
    private String browserInfo;

    @Column(name = "user_agent")
    private String userAgent;

    /** Zoho participant ID (from webhook payload) */
    @Column(name = "zoho_participant_id")
    private String zohoParticipantId;

    /** Last heartbeat received (for disconnect detection) */
    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum SessionStatus {
        ACTIVE, COMPLETED, DISCONNECTED
    }
}
