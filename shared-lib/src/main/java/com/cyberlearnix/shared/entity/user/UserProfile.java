package com.cyberlearnix.shared.entity.user;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    private String id; // Unique ID from AuthService

    @Column(name = "full_name")
    private String fullName;

    private String email;
    private String phone;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    private String role; // admin, teacher, student, dual

    @Column(name = "is_profile_complete")
    private Boolean isProfileComplete = false;

    private Integer age;
    private String bio;
    private String location;

    @Column(name = "date_of_birth")
    private String dateOfBirth;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
