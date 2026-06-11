package com.cyberlearnix.shared.entity.user;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false, unique = true, length = 2048)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiry;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    /** SHA-256 hash of userAgent+IP — token is rejected if device changes */
    @Column(name = "device_fingerprint", length = 64)
    private String deviceFingerprint;

    /** Token family ID — if a revoked token in this family is reused, all family tokens are invalidated */
    @Column(name = "family_id", length = 36)
    private String familyId;
}
