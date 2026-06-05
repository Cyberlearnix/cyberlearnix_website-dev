package com.cyberlearnix.lab.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;



@Entity
@Table(name = "course_lab_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseLabConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long courseId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lab_template_id", nullable = false)
    private LabTemplate labTemplate;

    /** If true, instructor requests must be admin-approved before container starts */
    @Column(nullable = false)
    private Boolean requiresApproval = true;

    @Column(nullable = false)
    private Boolean isActive = true;

    /** Admin user who created this config */
    private String createdBy;

    // ── Pre-installation build pipeline ───────────────────────────────────────

    /** Bash setup script written by admin; run inside a temp container during build. */
    @Column(columnDefinition = "TEXT")
    private String setupScript;

    /** Current state of the build pipeline for this course lab. */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SetupStatus setupStatus = SetupStatus.NOT_CONFIGURED;

    /** Full stdout+stderr log captured from the last build attempt. */
    @Column(columnDefinition = "TEXT")
    private String setupLog;

    /**
     * Docker image produced by the build but not yet published to students.
     * Populated after a successful build; reset on new build trigger.
     */
    private String stagedDockerImage;

    /**
     * Docker image currently active for student containers.
     * Promoted from stagedDockerImage via the publish action.
     * Falls back to labTemplate.dockerImage when null.
     */
    private String activeDockerImage;

    // ─────────────────────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
