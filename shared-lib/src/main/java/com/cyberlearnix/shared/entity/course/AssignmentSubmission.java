package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "assignment_submissions", indexes = {
    @Index(name = "idx_sub_content_id",  columnList = "content_id"),
    @Index(name = "idx_sub_student_id",  columnList = "student_id"),
    @Index(name = "idx_sub_status",      columnList = "status"),
    @Index(name = "idx_sub_course_id",   columnList = "course_id")
})
public class AssignmentSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "enrollment_id")
    private Long enrollmentId;

    /** CODING | FILE_UPLOAD | ESSAY | QUIZ | LAB_PRACTICAL | PROJECT */
    @Column(name = "submission_type")
    private String submissionType;

    /** Programming language for coding submissions */
    @Column(name = "language")
    private String language;

    @Column(name = "code", columnDefinition = "TEXT")
    private String code;

    /** JSON array of { name, url, size } for file submissions */
    @Column(name = "file_urls", columnDefinition = "TEXT")
    private String fileUrls;

    /** JSON array of URL strings for link submissions */
    @Column(name = "links", columnDefinition = "TEXT")
    private String links;

    @Column(name = "essay_text", columnDefinition = "TEXT")
    private String essayText;

    @Column(name = "word_count")
    private Integer wordCount;

    /** JSON map of { questionId: answer } for quiz submissions */
    @Column(name = "quiz_answers", columnDefinition = "TEXT")
    private String quizAnswers;

    /** JSON array of completed step IDs for lab practicals */
    @Column(name = "lab_completed_steps", columnDefinition = "TEXT")
    private String labCompletedSteps;

    /** JSON array of test result objects from auto-grading */
    @Column(name = "test_results", columnDefinition = "TEXT")
    private String testResults;

    /** Score from auto-grading (null until graded) */
    @Column(name = "auto_grade_score")
    private Integer autoGradeScore;

    /** Final score (from manual or auto grading) */
    @Column(name = "score")
    private Integer score;

    /** PENDING | AUTO_GRADED | GRADED | FLAGGED | RESUBMIT | LATE */
    @Column(name = "status")
    private String status = "PENDING";

    /** Plagiarism similarity score (0-100) */
    @Column(name = "plagiarism_score")
    private Integer plagiarismScore;

    /** AI-generated content probability (0-100) */
    @Column(name = "ai_generated_score")
    private Integer aiGeneratedScore;

    @Column(name = "attempt_number")
    private Integer attemptNumber = 1;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "internal_note", columnDefinition = "TEXT")
    private String internalNote;

    /** JSON rubric scores map { criterionId: points } */
    @Column(name = "rubric_scores", columnDefinition = "TEXT")
    private String rubricScores;

    @Column(name = "graded_by")
    private String gradedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) submittedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (attemptNumber == null) attemptNumber = 1;
    }
}
