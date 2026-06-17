package com.cyberlearnix.shared.entity.identity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "identity_enrollment_forms")
public class IdentityEnrollmentForm {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "role_type", nullable = false)
    private String roleType; // Role Type to be assigned upon approval

    @Column(name = "associated_course")
    private String associatedCourse;

    @Column(name = "custom_fields", columnDefinition = "TEXT")
    private String customFields; // JSON string representing custom fields

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "payment_required", nullable = false)
    private Boolean paymentRequired = false;

    @Column(name = "max_responses")
    private Integer maxResponses;

    @Column(nullable = false)
    private String status; // Active, Inactive

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
