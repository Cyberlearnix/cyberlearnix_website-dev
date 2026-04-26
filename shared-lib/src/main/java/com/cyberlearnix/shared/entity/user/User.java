package com.cyberlearnix.shared.entity.user;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role; // admin, teacher, student, dual, institute

    @Column(name = "is_first_login")
    private Boolean isFirstLogin = true;

    @Column(name = "failed_attempts")
    private Integer failedAttempts = 0;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
