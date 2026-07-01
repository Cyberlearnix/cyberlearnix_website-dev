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

    /** Role in the meeting — "presenter" or "attendee" (from Zoho participant report) */
    @Column(name = "role", length = 50)
    private String role;

    /** How the participant joined — "web", "mobile", "phone", etc. */
    @Column(name = "source", length = 50)
    private String source;

    /** Human-readable join/leave window from Zoho e.g. "02:20 PM - 02:21 PM" */
    @Column(name = "in_and_out_time", length = 100)
    private String inAndOutTime;

    /** Zoho member ZUID (unique across Zoho; absent for guest participants) */
    @Column(name = "member_id", length = 100)
    private String memberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}