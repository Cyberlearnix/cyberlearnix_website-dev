package com.cyberlearnix.shared.entity.enrollment;

import com.cyberlearnix.shared.util.RawJsonDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Multiple courses linked to this enrollment form.
     * When payment is approved, the student is enrolled in ALL of these courses.
     * Stored as a JSON array of Long IDs, e.g. [1, 2, 3].
     * Takes precedence over the legacy single courseId field if non-empty.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "course_ids", columnDefinition = "jsonb")
    private List<Long> courseIds = new ArrayList<>();

    /**
     * Returns the effective list of course IDs to enroll a student in.
     * Uses courseIds if present, falls back to the single courseId for backward compatibility.
     */
    public List<Long> getEffectiveCourseIds() {
        if (courseIds != null && !courseIds.isEmpty()) {
            return courseIds;
        }
        if (courseId != null) {
            return List.of(courseId);
        }
        return List.of();
    }

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
    @JsonAlias("coursePrice")
    private Double paymentAmount;

    /** Also expose as coursePrice in JSON for frontend compatibility. */
    public Double getCoursePrice() {
        return paymentAmount;
    }

    /** Accept coursePrice from admin UI JSON body. */
    public void setCoursePrice(Double price) {
        this.paymentAmount = price;
    }

    @Column(name = "payment_currency", length = 10)
    private String paymentCurrency = "INR";

    // ── Discount / Coupon ──────────────────────────────────────────────────────
    /** Whether a form-level discount is active and visible to students. */
    @Column(name = "discount_enabled", columnDefinition = "boolean default false")
    private Boolean discountEnabled = false;

    public boolean isDiscountEnabled() {
        return Boolean.TRUE.equals(discountEnabled);
    }

    /** PERCENTAGE or FLAT */
    @Column(name = "discount_type", length = 20)
    private String discountType;

    /** Discount value: percentage (0-100) or flat amount in INR. */
    @Column(name = "discount_value")
    private Double discountValue;

    /** Optional label shown to student e.g. "Early Bird", "Special Offer". */
    @Column(name = "discount_label", length = 100)
    private String discountLabel;

    /** Auto-generated coupon code exposed to students. */
    @Column(name = "discount_coupon_code", length = 50)
    private String discountCouponCode;

    // ─────────────────────────────────────────────────────────────────────────

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
