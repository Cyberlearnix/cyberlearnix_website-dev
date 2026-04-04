package com.cyberlearnix.shared.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "course_suggestions")
public class CourseSuggestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "admin_id")
    private String adminId;

    private String suggestion;
    private String status; // pending, resolved

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
