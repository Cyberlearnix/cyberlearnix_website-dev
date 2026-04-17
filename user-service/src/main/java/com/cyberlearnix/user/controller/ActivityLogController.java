package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.ActivityLog;
import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.user.ActivityLogRepository;
import com.cyberlearnix.shared.repository.user.UserProfileRepository;
import com.cyberlearnix.user.dto.UserActivityLogResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/activity/logs")
public class ActivityLogController {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    /** Existing: get logs for a specific user (limited list) */
    @GetMapping("/{userId}")
    public ResponseEntity<List<ActivityLog>> getUserLogs(
            @PathVariable String userId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(activityLogRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId));
    }

    /** Existing: create a log entry */
    @PostMapping
    public ResponseEntity<ActivityLog> logActivity(@RequestBody ActivityLog activityLog) {
        return ResponseEntity.ok(activityLogRepository.save(activityLog));
    }

    /**
     * Admin: paginated, enriched activity log with user profile details.
     *
     * Query params:
     *   role      — filter by user role: teacher | student | admin
     *   userId    — filter by specific user ID
     *   eventType — partial match on event type (case-insensitive)
     *   page      — 0-based page index (default 0)
     *   size      — page size (default 20, max 100)
     */
    @GetMapping
    public ResponseEntity<UserActivityLogResponse.PagedResponse> getAllLogs(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(size, 100);
        PageRequest pageRequest = PageRequest.of(page, safeSize, Sort.by("createdAt").descending());

        // Resolve the set of userIds to filter by
        List<String> targetUserIds = null;
        if (role != null && !role.isBlank()) {
            targetUserIds = userProfileRepository.findByRole(role.toLowerCase())
                    .stream().map(UserProfile::getId).collect(Collectors.toList());
            if (targetUserIds.isEmpty()) {
                return ResponseEntity.ok(UserActivityLogResponse.PagedResponse.builder()
                        .content(List.of()).page(page).size(safeSize)
                        .totalElements(0).totalPages(0).build());
            }
        }

        // If userId is specified, intersect with role filter
        if (userId != null && !userId.isBlank()) {
            if (targetUserIds == null || targetUserIds.contains(userId)) {
                targetUserIds = List.of(userId);
            } else {
                return ResponseEntity.ok(UserActivityLogResponse.PagedResponse.builder()
                        .content(List.of()).page(page).size(safeSize)
                        .totalElements(0).totalPages(0).build());
            }
        }

        // Query logs
        Page<ActivityLog> logPage = queryLogs(targetUserIds, eventType, pageRequest);

        // Collect unique user IDs from the page of logs
        List<String> logUserIds = logPage.getContent().stream()
                .map(ActivityLog::getUserId).distinct().collect(Collectors.toList());

        // Batch-fetch profiles for this page
        Map<String, UserProfile> profileMap = userProfileRepository.findAllById(logUserIds)
                .stream().collect(Collectors.toMap(UserProfile::getId, Function.identity()));

        // Build enriched response
        List<UserActivityLogResponse> enriched = logPage.getContent().stream()
                .map(log -> toResponse(log, profileMap.get(log.getUserId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(UserActivityLogResponse.PagedResponse.builder()
                .content(enriched)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .build());
    }

    private Page<ActivityLog> queryLogs(List<String> userIds, String eventType, PageRequest pageRequest) {
        boolean hasUsers = userIds != null && !userIds.isEmpty();
        boolean hasEvent = eventType != null && !eventType.isBlank();

        if (hasUsers && hasEvent) {
            return activityLogRepository
                    .findByUserIdInAndEventTypeContainingIgnoreCaseOrderByCreatedAtDesc(userIds, eventType, pageRequest);
        } else if (hasUsers) {
            return activityLogRepository
                    .findByUserIdInOrderByCreatedAtDesc(userIds, pageRequest);
        } else if (hasEvent) {
            return activityLogRepository
                    .findByEventTypeContainingIgnoreCaseOrderByCreatedAtDesc(eventType, pageRequest);
        } else {
            return activityLogRepository.findAllByOrderByCreatedAtDesc(pageRequest);
        }
    }

    private UserActivityLogResponse toResponse(ActivityLog log, UserProfile profile) {
        UserActivityLogResponse.UserSummary userSummary = profile != null
                ? UserActivityLogResponse.UserSummary.builder()
                        .id(profile.getId())
                        .fullName(profile.getFullName())
                        .email(profile.getEmail())
                        .role(profile.getRole())
                        .photoUrl(profile.getPhotoUrl())
                        .isActive(profile.getIsActive())
                        .build()
                : UserActivityLogResponse.UserSummary.builder()
                        .id(log.getUserId())
                        .fullName("Unknown")
                        .email(null)
                        .role(null)
                        .photoUrl(null)
                        .isActive(null)
                        .build();

        return UserActivityLogResponse.builder()
                .logId(log.getId())
                .eventType(log.getEventType())
                .description(log.getDescription())
                .metadata(log.getMetadata())
                .createdAt(log.getCreatedAt())
                .user(userSummary)
                .build();
    }
}
