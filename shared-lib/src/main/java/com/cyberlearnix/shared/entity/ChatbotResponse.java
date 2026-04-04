package com.cyberlearnix.shared.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "chatbot_responses")
public class ChatbotResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String intent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String response;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> trainingPhrases = new HashMap<>();

    @Column(name = "category")
    private String category;

    @Column(name = "confidence_threshold")
    private Double confidenceThreshold = 0.7;

    @Column(name = "requires_followup")
    private Boolean requiresFollowup = false;

    @Column(name = "followup_questions", columnDefinition = "TEXT")
    private String followupQuestions;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "usage_count")
    private Integer usageCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
