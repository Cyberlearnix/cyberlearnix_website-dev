package com.cyberlearnix.shared.entity.enrollment;

import com.cyberlearnix.shared.util.RawJsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "enrollment_forms_config")
public class EnrollmentFormConfig {
    @Id
    private String id;

    private String title;
    private String description;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "enrollee_role", length = 20)
    private String enrolleeRole = "student";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @JsonDeserialize(using = RawJsonDeserializer.class)
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
    @JsonDeserialize(using = RawJsonDeserializer.class)
    private String quizSettings;

    @Column(name = "limit_one_response")
    private boolean limitOneResponse = false;

    // ── Payment ──────────────────────────────────────────────────────────────
    @Column(name = "payment_enabled", columnDefinition = "boolean default false")
    private Boolean paymentEnabled = false;

    /** Null-safe convenience accessor — existing rows with NULL are treated as false. */
    public boolean isPaymentEnabled() {
        return Boolean.TRUE.equals(paymentEnabled);
    }

    @Column(name = "payment_amount")
    private Double paymentAmount;

    @Column(name = "payment_currency", length = 10)
    private String paymentCurrency = "INR";

    // ─────────────────────────────────────────────────────────────────────────

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
