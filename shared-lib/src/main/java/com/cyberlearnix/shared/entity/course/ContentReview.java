package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "content_reviews")
public class ContentReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "review_status")
    private String reviewStatus = "pending";

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "is_approved")
    private Boolean isApproved;

    @Column(name = "requires_revision")
    private Boolean requiresRevision = false;

    @Column(name = "revision_notes", columnDefinition = "TEXT")
    private String revisionNotes;

    @PreUpdate
    public void preUpdate() {
        if ("reviewed".equals(this.reviewStatus) && this.reviewedAt == null) {
            this.reviewedAt = LocalDateTime.now();
        }
    }
}
