package com.cyberlearnix.notification.service;

import com.cyberlearnix.notification.dto.InboxNotificationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains open SSE connections keyed by userId.
 * When a new notification is persisted, call {@link #push} to deliver it in real-time.
 */
@Component
@Slf4j
public class NotificationSseEmitterRegistry {

    // userId → emitter  (one active stream per user)
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** Register a new SSE connection for this user. Closes any previous connection. */
    public SseEmitter register(String userId) {
        // Remove previous emitter for this user
        SseEmitter existing = emitters.remove(userId);
        if (existing != null) {
            try { existing.complete(); } catch (Exception ignored) {}
        }

        // 5-minute timeout — the frontend will reconnect automatically
        SseEmitter emitter = new SseEmitter(300_000L);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> {
            emitters.remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(userId, emitter));

        // Send a heartbeat immediately so the client knows the connection is live
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(userId, emitter);
        }

        log.debug("SSE registered for userId={}", userId);
        return emitter;
    }

    /** Push a notification to a connected user (no-op if not connected). */
    public void push(String userId, InboxNotificationDTO notification) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(notification));
        } catch (IOException e) {
            log.debug("SSE push failed for userId={}, removing emitter", userId);
            emitters.remove(userId, emitter);
        }
    }

    /** Broadcast to all connected users (used for admin announcements). */
    public void pushToAll(InboxNotificationDTO notification) {
        emitters.keySet().forEach(uid -> push(uid, notification));
    }
}
