package com.cyberlearnix.admin.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "teams_meetings")
public class TeamsMeeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Subject / title of the Teams meeting */
    @Column(nullable = false)
    private String subject;

    /** Azure Graph online meeting ID returned after creation */
    @Column(name = "graph_meeting_id", unique = true)
    private String graphMeetingId;

    /** Clickable URL participants open to join the meeting */
    @Column(name = "join_url", length = 2048)
    private String joinUrl;

    /** Host-only URL to start/launch the meeting (from Zoho GET session response) */
    @Column(name = "start_link", length = 2048)
    private String startLink;

    /** Meeting password returned by Zoho */
    @Column(name = "password")
    private String password;

    // ── Recurring fields ──────────────────────────────────────────────────────

    @Column(name = "recurring", nullable = false, columnDefinition = "boolean not null default false")
    private boolean recurring = false;

    /** DAILY, WEEKLY, MONTHLY — null for non-recurring meetings */
    @Column(name = "repeat_type")
    private String repeatType;

    @Column(name = "repeat_every")
    private Integer repeatEvery;

    @Column(name = "recurrence_end_date")
    private LocalDate recurrenceEndDate;

    /** Azure AD object ID of the organizer account */
    @Column(name = "organizer_user_id", nullable = false)
    private String organizerUserId;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @Column(length = 2000)
    private String description;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "batch_id")
    private String batchId;

    /** SCHEDULED | CANCELLED */
    @Column(nullable = false)
    private String status = "SCHEDULED";

    /** ID of the admin who scheduled this meeting */
    @Column(name = "created_by")
    private String createdBy;

    /**
     * JSON array of invited panelists stored as text, e.g.
     * [{"name":"John","email":"john@example.com"}]
     */
    @Column(name = "invitees_json", columnDefinition = "TEXT")
    private String inviteesJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "participants_synced_at")
    private LocalDateTime participantsSyncedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
