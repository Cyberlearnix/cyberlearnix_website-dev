package com.cyberlearnix.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Long expiresIn; // Token expiration time in seconds
    private String tokenType; // Usually "Bearer"

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
