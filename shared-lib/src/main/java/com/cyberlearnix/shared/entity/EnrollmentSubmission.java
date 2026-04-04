package com.cyberlearnix.shared.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "enrollment_submissions")
public class EnrollmentSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name")
    private String fullName;

    private String email;
    private String phone;

    @Column(name = "student_email")
    private String studentEmail;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "amount_paid")
    private Double amountPaid;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "screenshot_url")
    private String screenshotUrl;

    @Column(name = "student_data", columnDefinition = "jsonb")
    private String studentData;

    @Column(name = "payment_status")
    private String paymentStatus = "PENDING";

    private String status = "PENDING"; // Can be PENDING, VERIFIED, REJECTED

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

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
