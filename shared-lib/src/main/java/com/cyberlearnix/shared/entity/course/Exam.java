package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "exams", indexes = {
    @Index(name = "idx_exam_course_id",   columnList = "course_id"),
    @Index(name = "idx_exam_status",      columnList = "status"),
    @Index(name = "idx_exam_created_by",  columnList = "created_by")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String subtitle;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "sub_chapter_id")
    private Long subChapterId;

    @Column(name = "exam_type", length = 50)
    private String examType; // QUIZ, MIDTERM, FINAL, PRACTICE, MOCK, CERTIFICATION

    @Column(length = 20)
    private String difficulty; // EASY, MEDIUM, HARD, EXPERT

    @Column(length = 100)
    private String category;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "total_marks")
    private Integer totalMarks;

    @Column(name = "passing_marks")
    private Integer passingMarks;

    @Column(length = 20)
    @Builder.Default
    private String status = "DRAFT"; // DRAFT, PUBLISHED, SCHEDULED, ARCHIVED

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "available_from")
    private LocalDateTime availableFrom;

    @Column(name = "available_until")
    private LocalDateTime availableUntil;

    @Column(name = "max_attempts")
    @Builder.Default
    private Integer maxAttempts = 1;

    @Column(name = "randomize_questions")
    @Builder.Default
    private Boolean randomizeQuestions = false;

    @Column(name = "randomize_options")
    @Builder.Default
    private Boolean randomizeOptions = false;

    @Column(name = "show_results_immediately")
    @Builder.Default
    private Boolean showResultsImmediately = true;

    @Column(name = "negative_marking")
    @Builder.Default
    private Boolean negativeMarking = false;

    @Column(name = "negative_mark_value")
    @Builder.Default
    private Double negativeMarkValue = 0.0;

    // ─── Security / Proctoring ────────────────────────────────────────────────

    @Column(name = "browser_lockdown")
    @Builder.Default
    private Boolean browserLockdown = false;

    @Column(name = "webcam_proctoring")
    @Builder.Default
    private Boolean webcamProctoring = false;

    @Column(name = "tab_switch_detection")
    @Builder.Default
    private Boolean tabSwitchDetection = false;

    @Column(name = "ai_monitoring")
    @Builder.Default
    private Boolean aiMonitoring = false;

    @Column(name = "max_violations")
    @Builder.Default
    private Integer maxViolations = 3;

    @Column(name = "copy_paste_blocked")
    @Builder.Default
    private Boolean copyPasteBlocked = false;

    @Column(name = "right_click_disabled")
    @Builder.Default
    private Boolean rightClickDisabled = false;

    @Column(name = "fullscreen_enforced")
    @Builder.Default
    private Boolean fullscreenEnforced = false;

    @Column(name = "ip_logging")
    @Builder.Default
    private Boolean ipLogging = false;

    @Column(name = "session_monitoring")
    @Builder.Default
    private Boolean sessionMonitoring = false;

    // ─── Metadata ─────────────────────────────────────────────────────────────

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(name = "question_count")
    @Builder.Default
    private Integer questionCount = 0;

    @Column(name = "exam_metadata", columnDefinition = "TEXT")
    private String examMetadata; // JSON for extra fields

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
