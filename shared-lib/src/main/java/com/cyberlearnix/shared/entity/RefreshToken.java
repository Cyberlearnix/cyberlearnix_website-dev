package com.cyberlearnix.shared.entity;

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
}
