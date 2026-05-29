package com.cyberlearnix.shared.entity.form;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "general_forms")
public class GeneralForm {
    @Id
    private String id;

    private String title;
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String fields;

    @Column(name = "is_active")
    private boolean isActive = true;

    private String token;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "is_quiz")
    private boolean isQuiz = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quiz_settings", columnDefinition = "jsonb")
    private String quizSettings;

    @Column(name = "limit_one_response")
    private boolean limitOneResponse = false;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "payment_enabled")
    private boolean paymentEnabled = false;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "payment_amount")
    private Double paymentAmount;

    @Column(name = "gst_percent")
    private Integer gstPercent;

    @Column(name = "gst_amount")
    private Double gstAmount;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
