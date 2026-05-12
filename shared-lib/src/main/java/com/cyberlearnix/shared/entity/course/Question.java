package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_questions", indexes = {
    @Index(name = "idx_question_exam_id",   columnList = "exam_id"),
    @Index(name = "idx_question_active",    columnList = "is_active"),
    @Index(name = "idx_question_type",      columnList = "question_type"),
    @Index(name = "idx_question_order",     columnList = "exam_id, order_index")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_id")
    private Long examId;

    @Column(name = "question_bank_id")
    private Long questionBankId; // reference to bank question (if copied from bank)

    @Column(name = "question_type", length = 30, nullable = false)
    private String questionType; // MCQ, MULTI_CORRECT, TRUE_FALSE, FILL_BLANK, MATCH, DESCRIPTIVE, ESSAY, CODING, SQL, LAB_TASK, FILE_UPLOAD

    @Column(name = "question_text", columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @Column(columnDefinition = "TEXT")
    private String options; // JSON array of {id, text} objects

    @Column(name = "correct_answer", columnDefinition = "TEXT")
    private String correctAnswer; // JSON (string, array, or object depending on type)

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(nullable = false)
    @Builder.Default
    private Double marks = 1.0;

    @Column(name = "negative_marks")
    @Builder.Default
    private Double negativeMarks = 0.0;

    @Column(length = 20)
    @Builder.Default
    private String difficulty = "MEDIUM";

    @Column(name = "order_index")
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(length = 100)
    private String subject;

    @Column(length = 100)
    private String topic;

    // ─── Coding/SQL specific ──────────────────────────────────────────────────

    @Column(length = 30)
    private String language; // python, java, javascript, cpp, sql, etc.

    @Column(name = "starter_code", columnDefinition = "TEXT")
    private String starterCode;

    @Column(name = "test_cases", columnDefinition = "TEXT")
    private String testCases; // JSON array

    @Column(name = "time_limit_seconds")
    @Builder.Default
    private Integer timeLimitSeconds = 5;

    @Column(name = "memory_limit_mb")
    @Builder.Default
    private Integer memoryLimitMb = 256;

    // ─── Media ────────────────────────────────────────────────────────────────

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "video_url")
    private String videoUrl;

    // ─── Metadata ─────────────────────────────────────────────────────────────

    @Column(name = "question_metadata", columnDefinition = "TEXT")
    private String questionMetadata; // JSON for extra fields

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_from_bank")
    @Builder.Default
    private Boolean isFromBank = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
