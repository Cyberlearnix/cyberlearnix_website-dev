package com.cyberlearnix.lab.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "lab_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    private Long instructorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_template_id", nullable = false)
    private LabTemplate labTemplate;

    /** Docker container ID assigned to this lab session */
    private String containerId;

    /** Human-readable container name: cyberlearnix-lab-{studentId}-{assignmentId} */
    private String containerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status = AssignmentStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant assignedAt;

    /** Updated on each terminal interaction; used for idle detection */
    private Instant lastActiveAt;

    private Instant expiresAt;

    @PrePersist
    void prePersist() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }
}
