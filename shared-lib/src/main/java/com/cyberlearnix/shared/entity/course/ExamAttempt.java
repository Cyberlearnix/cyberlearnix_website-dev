package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_attempts", indexes = {
    @Index(name = "idx_attempt_exam_id",    columnList = "exam_id"),
    @Index(name = "idx_attempt_student_id", columnList = "student_id"),
    @Index(name = "idx_attempt_status",     columnList = "status"),
    @Index(name = "idx_attempt_exam_student", columnList = "exam_id, student_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "enrollment_id")
    private Long enrollmentId;

    @Column(name = "attempt_number")
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "IN_PROGRESS"; // IN_PROGRESS, SUBMITTED, GRADED, TERMINATED, TIMED_OUT

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    @Column(name = "time_spent_seconds")
    @Builder.Default
    private Integer timeSpentSeconds = 0;

    @Column(name = "remaining_seconds")
    private Integer remainingSeconds;

    // ─── Answers ──────────────────────────────────────────────────────────────

    @Column(columnDefinition = "TEXT")
    private String answers;

    // ─── Results ──────────────────────────────────────────────────────────────

    @Column
    private Integer score;

    @Column(name = "total_marks")
    private Integer totalMarks;

    @Column
    private Double percentage;

    @Column
    private Boolean passed;

    @Column(name = "question_scores", columnDefinition = "TEXT")
    private String questionScores; // JSON map: { questionId: score }

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "graded_by")
    private String gradedBy;

    // ─── Proctoring / Anti-cheat ─────────────────────────────────────────────

    @Column(name = "tab_switch_count")
    @Builder.Default
    private Integer tabSwitchCount = 0;

    @Column(name = "violation_count")
    @Builder.Default
    private Integer violationCount = 0;

    @Column(columnDefinition = "TEXT")
    private String violations; // JSON array of { type, timestamp, detail }

    // ─── Session info ─────────────────────────────────────────────────────────

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "device_info", length = 256)
    private String deviceInfo;

    @Column(name = "attempt_metadata", columnDefinition = "TEXT")
    private String attemptMetadata; // JSON for extra fields

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = LocalDateTime.now();
    }
}
