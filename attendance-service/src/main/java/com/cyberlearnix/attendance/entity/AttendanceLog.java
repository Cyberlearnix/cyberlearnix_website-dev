package com.cyberlearnix.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Raw webhook event log — immutable audit trail of every Zoho event.
 */
@Data
@Entity
@Table(name = "attendance_logs", indexes = {
    @Index(name = "idx_al_meeting_id", columnList = "meetingId"),
    @Index(name = "idx_al_student_id", columnList = "studentId"),
    @Index(name = "idx_al_event_type", columnList = "eventType"),
    @Index(name = "idx_al_occurred_at", columnList = "occurredAt")
})
@EntityListeners(AuditingEntityListener.class)
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "meeting_id", nullable = false)
    private String meetingId;

    @Column(name = "student_id")
    private String studentId;

    @Column(name = "student_email")
    private String studentEmail;

    /** meeting_started | meeting_ended | participant_joined | participant_left | participant_rejoined | heartbeat */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** Raw JSON payload from Zoho webhook */
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "zoho_participant_id")
    private String zohoParticipantId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
