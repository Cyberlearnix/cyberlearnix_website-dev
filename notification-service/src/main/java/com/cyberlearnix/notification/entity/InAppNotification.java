package com.cyberlearnix.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "in_app_notifications", indexes = {
        @Index(name = "idx_notification_user_id",     columnList = "user_id"),
        @Index(name = "idx_notification_created_at",  columnList = "created_at DESC"),
        @Index(name = "idx_notification_user_unread",  columnList = "user_id, read")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Target user's string ID (matches X-User-Id injected by gateway).
     * For broadcast notifications this is populated per-row (one row per recipient).
     */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * Notification category. Allowed values:
     * COURSE_ASSIGNED, LIVE_SESSION, ANNOUNCEMENT, CONTENT_ADDED, ACHIEVEMENT, SYSTEM
     */
    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    /** Optional deep-link (e.g. /courses/42, /meetings/7). */
    @Column(length = 512)
    private String link;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
