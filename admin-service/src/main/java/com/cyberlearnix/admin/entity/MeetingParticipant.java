package com.cyberlearnix.admin.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Attendance record for a single participant who joined a Zoho meeting.
 *
 * Populated by ZohoSyncService.fetchAndStoreParticipants() once a meeting has
 * ended, pulled from:
 *   GET /api/v2/{orgId}/sessions/{sessionKey}/attendees.json
 *
 * Uses a flat meeting_id FK (no JPA relation) to match the style of
 * TeamsMeeting and avoid serialization headaches.
 */
@Data
@Entity
@Table(
        name = "meeting_participants",
        indexes = {
                @Index(name = "idx_participant_meeting", columnList = "meeting_id"),
                @Index(name = "idx_participant_email", columnList = "email")
        }
)
public class MeetingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → teams_meetings.id */
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /** Display name from Zoho, if provided */
    @Column(length = 255)
    private String name;

    /** Email used to join the meeting */
    @Column(nullable = false, length = 255)
    private String email;

    /** When the participant joined (from Zoho) */
    @Column(name = "join_time")
    private LocalDateTime joinTime;

    /** When the participant left (from Zoho) */
    @Column(name = "leave_time")
    private LocalDateTime leaveTime;

    /** Total time present in seconds */
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}