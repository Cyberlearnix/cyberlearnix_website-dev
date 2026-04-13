package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "content_progress", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "student_id", "content_id" })
})
public class ContentProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "status")
    private String status; // STARTED, COMPLETED

    @Column(name = "is_completed")
    private Boolean isCompleted = false;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "video_time_seconds")
    private Integer videoTimeSeconds = 0; // For tracking video resume point

    @Column(name = "score")
    private Double score; // For quizzes/assignments
}
