package com.cyberlearnix.admin.service;

import com.cyberlearnix.admin.client.NotificationClient;
import com.cyberlearnix.admin.entity.Meeting;
import com.cyberlearnix.admin.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends in-app meeting reminders:
 * 1. Immediately when a meeting is created (via sendCreationNotification).
 * 2. Every day at 8:00 AM IST for meetings starting within the next 2 hours.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingReminderService {

    private final MeetingRepository meetingRepository;
    private final NotificationClient notificationClient;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy 'at' hh:mm a");

    // ── Called after meeting creation ─────────────────────────────────────────
    public void sendCreationNotification(Meeting meeting) {
        try {
            String timeStr = meeting.getStartTime() != null
                    ? meeting.getStartTime().format(TIME_FMT) : "a scheduled time";

            Map<String, Object> payload = buildPayload(
                    meeting,
                    "📹 New Meeting Scheduled",
                    "\"" + meeting.getTitle() + "\" is scheduled for " + timeStr
                            + ". Join with your Student ID as your display name.",
                    meeting.getJoinUrl()
            );

            notificationClient.createInAppNotification("true", payload);
            log.info("[MeetingReminder] Creation notification sent for meeting {}", meeting.getId());
        } catch (Exception e) {
            log.warn("[MeetingReminder] Could not send creation notification: {}", e.getMessage());
        }
    }

    // ── Scheduled: daily 8 AM, reminds for meetings in next 2 hours ──────────
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    public void sendDailyReminders() {
        log.info("[MeetingReminder] Running daily reminder job");
        LocalDateTime now = LocalDateTime.now();
        List<Meeting> upcoming = meetingRepository.findMeetingsInWindow(now, now.plusHours(2));

        for (Meeting m : upcoming) {
            try {
                String timeStr = m.getStartTime().format(TIME_FMT);
                Map<String, Object> payload = buildPayload(
                        m,
                        "⏰ Meeting Reminder: " + m.getTitle(),
                        "Your meeting starts at " + timeStr
                                + ". Enter with your Student ID (e.g. CLX-INT-2026-XXXX) as your display name.",
                        m.getJoinUrl()
                );
                notificationClient.createInAppNotification("true", payload);
                log.info("[MeetingReminder] Reminder sent for meeting {}", m.getId());
            } catch (Exception e) {
                log.warn("[MeetingReminder] Could not send reminder for meeting {}: {}", m.getId(), e.getMessage());
            }
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private Map<String, Object> buildPayload(Meeting m, String title, String body, String link) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "LIVE_SESSION");
        payload.put("title", title);
        payload.put("body", body);
        if (link != null) payload.put("link", link);
        // Notify all enrolled students in the course, or all students if no course
        if (m.getCourseId() != null) {
            payload.put("courseId", m.getCourseId());
        } else {
            payload.put("targetRole", "STUDENT");
        }
        return payload;
    }
}
