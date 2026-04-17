package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin API to track user activity — what teachers and students have done.
 *
 * Proxies to user-service's enriched activity log endpoint, which returns
 * each log entry combined with the user's profile details (name, email, role, photo).
 */
@RestController
@RequestMapping("/api/admin/user-activity")
@RequiredArgsConstructor
public class UserActivityController {

    private final UserServiceClient userServiceClient;

    /**
     * Get paginated activity logs with optional filters.
     *
     * @param role      filter by user role: teacher | student | admin (optional)
     * @param userId    filter by specific user ID (optional)
     * @param eventType partial match on event type, e.g. LOGIN, ENROLL, SUBMIT (optional)
     * @param page      0-based page number (default 0)
     * @param size      page size, max 100 (default 20)
     * @param auth      Bearer token from request header
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getUserActivityLogs(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String auth) {

        Map<String, Object> result = userServiceClient.getActivityLogs(
                role, userId, eventType, page, size, auth);
        return ResponseEntity.ok(result);
    }
}
