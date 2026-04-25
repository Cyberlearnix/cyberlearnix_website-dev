package com.cyberlearnix.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enriched activity log entry returned to admins.
 * Combines the raw ActivityLog with the user's profile details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityLogResponse {

    private Long logId;
    private String eventType;
    private String description;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    // User info embedded
    private UserSummary user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummary {
        private String id;
        private String fullName;
        private String email;
        private String role;
        private String photoUrl;
        private Boolean isActive;
    }

    // Paginated wrapper
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PagedResponse {
        private List<UserActivityLogResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}
