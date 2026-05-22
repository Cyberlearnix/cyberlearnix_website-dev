package com.cyberlearnix.notification.dto;

import lombok.Data;

import java.util.List;

/**
 * Inbound payload for creating in-app notifications.
 * Used by other microservices calling POST /api/notifications/inbox
 * and POST /api/notifications/inbox/batch.
 */
@Data
public class CreateInboxNotificationRequest {

    /** Single target user. Use this OR userIds, not both. */
    private String userId;

    /** Multiple target users (batch creation). */
    private List<String> userIds;

    /** Notification category: COURSE_ASSIGNED, LIVE_SESSION, ANNOUNCEMENT, CONTENT_ADDED, ACHIEVEMENT, SYSTEM */
    private String type;

    private String title;
    private String body;

    /** Optional deep-link shown as CTA in the notification card. */
    private String link;

    // ── Admin broadcast helpers ──────────────────────────────────────────────

    /**
     * When set, notification-service will resolve all users with this role
     * (via user-service) and create a notification for each.
     * Values: STUDENT | TEACHER | ADMIN
     */
    private String targetRole;

    /**
     * When set, notification-service will resolve all students enrolled in
     * this course (via enrollment-service) and create a notification for each.
     */
    private Long courseId;
}
