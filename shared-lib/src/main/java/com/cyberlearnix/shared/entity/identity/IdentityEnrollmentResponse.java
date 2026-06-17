package com.cyberlearnix.shared.entity.identity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "identity_enrollment_responses")
public class IdentityEnrollmentResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "form_id", nullable = false)
    private String formId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    @Column(name = "custom_answers", columnDefinition = "TEXT")
    private String customAnswers; // JSON string representing responses to custom fields

    @Column(nullable = false)
    private String status = "Pending"; // Pending, Approved, Rejected, ChangesRequested

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
