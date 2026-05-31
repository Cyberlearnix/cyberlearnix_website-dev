package com.cyberlearnix.lab.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "lab_approval_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private String studentId;

    private String requestedByInstructorId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lab_template_id", nullable = false)
    private LabTemplate labTemplate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    private String rejectionReason;

    private String approvedByAdminId;

    @Column(nullable = false, updatable = false)
    private Instant requestedAt;

    private Instant decidedAt;

    /** Filled after approval creates a LabAssignment */
    private Long resultingAssignmentId;

    @PrePersist
    void prePersist() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
