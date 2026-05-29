package com.cyberlearnix.notification.controller;

import com.cyberlearnix.notification.dto.CreateInboxNotificationRequest;
import com.cyberlearnix.notification.dto.InboxNotificationDTO;
import com.cyberlearnix.notification.service.InAppNotificationService;
import com.cyberlearnix.notification.service.NotificationSseEmitterRegistry;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * In-app notification inbox API.
 *
 * All student/teacher/admin endpoints read the caller's identity from the
 * {@code X-User-Id} header injected by the API gateway.
 *
 * Internal service-to-service creation endpoints ({@code POST /inbox} and
 * {@code POST /inbox/batch}) require the {@code X-Internal-Service: true} header
 * so they are not reachable from the public internet (the gateway does not forward
 * this header from external clients).
 */
@RestController
@RequestMapping("/api/notifications/inbox")
@RequiredArgsConstructor
public class InAppNotificationController {

    private final InAppNotificationService service;
    private final NotificationSseEmitterRegistry sseRegistry;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // ═══════════════════════════════════════════════════════════════════════════
    // Student / Teacher / Admin — personal inbox
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/notifications/inbox
     * Returns all notifications for the authenticated user, newest first.
     */
    @GetMapping
    public ResponseEntity<List<InboxNotificationDTO>> getInbox(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        userId = resolveUserId(userId);
        return ResponseEntity.ok(service.getInbox(userId));
    }

    /**
     * GET /api/notifications/inbox/count
     * Returns unread count for badge display.
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        userId = resolveUserId(userId);
        return ResponseEntity.ok(Map.of("unread", service.getUnreadCount(userId)));
    }

    /**
     * GET /api/notifications/inbox/stream
     * SSE stream — client subscribes once and receives real-time pushes.
     *
     * The native browser EventSource API cannot send custom headers, so the JWT
     * may be passed as a {@code ?token=} query parameter instead of an
     * Authorization header.  The JwtTokenFilter handles the header case; we
     * handle the query-param case here by extracting the userId from the token.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "token", required = false) String tokenParam) {
        if ((userId == null || userId.isBlank()) && tokenParam != null && !tokenParam.isBlank()) {
            userId = extractUserIdFromToken(tokenParam);
        }
        userId = resolveUserId(userId);
        return sseRegistry.register(userId);
    }

    /**
     * PATCH /api/notifications/inbox/{id}/read
     * Mark a single notification as read.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<InboxNotificationDTO> markRead(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        userId = resolveUserId(userId);
        return ResponseEntity.ok(service.markRead(id, userId));
    }

    /**
     * PATCH /api/notifications/inbox/read-all
     * Mark all notifications as read for the current user.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        userId = resolveUserId(userId);
        int updated = service.markAllRead(userId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    /**
     * DELETE /api/notifications/inbox/{id}
     * Delete a single notification owned by the current user.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteOne(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        userId = resolveUserId(userId);
        service.delete(id, userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * DELETE /api/notifications/inbox/clear-all
     * Clear the entire inbox for the current user.
     */
    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Boolean>> clearAll(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        userId = resolveUserId(userId);
        service.clearAll(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal service-to-service creation (called by enrollment/admin/course services)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/notifications/inbox
     * Create notification(s). Accepts the full {@link CreateInboxNotificationRequest}
     * which supports: single userId, userIds list, courseId (resolves enrolled students),
     * or targetRole (resolves all users with that role).
     *
     * Protected by X-Internal-Service header (set only by internal services, not forwarded
     * from outside by the gateway).
     */
    @PostMapping
    public ResponseEntity<List<InboxNotificationDTO>> createNotification(
            @RequestBody CreateInboxNotificationRequest request,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalFlag) {
        requireInternal(internalFlag);
        return ResponseEntity.ok(service.processRequest(request));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Admin management endpoints
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/notifications/inbox/admin/send
     * Admin sends a notification to specific user IDs or to all students.
     */
    @PostMapping("/admin/send")
    public ResponseEntity<List<InboxNotificationDTO>> adminSend(
            @RequestBody CreateInboxNotificationRequest request) {
        requireAdminRole();
        return ResponseEntity.ok(service.processRequest(request));
    }

    /**
     * GET /api/notifications/inbox/admin/history
     * Returns the last 500 sent notifications (admin view).
     */
    @GetMapping("/admin/history")
    public ResponseEntity<List<InboxNotificationDTO>> adminHistory() {
        requireAdminRole();
        return ResponseEntity.ok(service.getAdminHistory());
    }

    /**
     * DELETE /api/notifications/inbox/admin/recall/{id}
     * Recall (delete) a broadcast notification by batch (same type+title+creation time window).
     */
    @DeleteMapping("/admin/recall/{id}")
    public ResponseEntity<Map<String, Integer>> adminRecall(@PathVariable Long id) {
        requireAdminRole();
        int deleted = service.recallNotification(id);
        return ResponseEntity.ok(Map.of("recalled", deleted));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Extract userId (JWT subject) from a raw token string. Returns null on failure. */
    private String extractUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.trim().getBytes()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUserId(String headerValue) {
        if (headerValue != null && !headerValue.isBlank()) return headerValue;
        // Fallback: extract from Spring Security principal name (set by JwtAuthFilter)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) return auth.getName();
        throw new SecurityException("User identity could not be determined");
    }

    private void requireAdminRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                             || a.getAuthority().equals("ROLE_SUPER_ADMIN"))) {
            throw new org.springframework.security.access.AccessDeniedException("Admin role required");
        }
    }

    private void requireInternal(String internalFlag) {
        if (!"true".equalsIgnoreCase(internalFlag)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This endpoint is for internal service-to-service calls only");
        }
    }
}
