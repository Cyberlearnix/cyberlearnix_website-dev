package com.cyberlearnix.shared.entity.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity to track authentication events for security auditing
 * Logs all login attempts (success/failure), logouts, and token refreshes
 */
@Data
@Entity
@Table(name = "auth_audits", indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_ip_address", columnList = "ip_address"),
        @Index(name = "idx_timestamp", columnList = "timestamp"),
        @Index(name = "idx_action", columnList = "action")
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthAudit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String action; // LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, TOKEN_REFRESH, PASSWORD_CHANGED

    @Column(name = "ip_address", nullable = false)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "reason")
    private String reason; // Details about failure, e.g., "Invalid password"

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(name = "success")
    @Builder.Default
    private Boolean success = true;

    @Column(name = "user_id")
    private String userId; // Set after successful authentication
}
