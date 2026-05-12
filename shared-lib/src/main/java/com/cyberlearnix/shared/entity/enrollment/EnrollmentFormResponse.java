package com.cyberlearnix.shared.entity.enrollment;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "enrollment_form_responses")
public class EnrollmentFormResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id")
    private String formId;

    @Column(name = "student_email")
    private String studentEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "student_data", columnDefinition = "jsonb")
    private String studentData;

    @Column(name = "amount_paid")
    private Double amountPaid;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "screenshot_url")
    private String screenshotUrl;

    @Column(name = "payment_status")
    private String paymentStatus = "PENDING";

    @Column(name = "payment_mode")
    private String paymentMode;

    @Column(name = "mihpayid")
    private String mihpayid;

    @Column(name = "bank_ref_num")
    private String bankRefNum;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payu_response", columnDefinition = "jsonb")
    private String payuResponse;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_user_id")
    private String createdUserId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
