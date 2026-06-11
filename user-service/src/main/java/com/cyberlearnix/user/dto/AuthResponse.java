package com.cyberlearnix.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Secure response DTO for authentication endpoints
 * Does NOT include sensitive data like refresh token in body
 * Refresh token is sent only via httpOnly cookie
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    
    private String token; // Access token
    private UserInfo user;
    private Long expiresIn;        // Access token TTL in seconds
    private String tokenType;      // Usually "Bearer"
    private Long refreshExpiresIn; // Refresh token TTL in seconds (30 days = 2592000)
    private String refreshExpiresAt; // Refresh token absolute expiry as ISO-8601 UTC string

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        private String id;
        private String email;
        private String role;
        private Boolean isFirstLogin;
        private String lastLoginAt; // Previous login time, null if first login
    }
}
