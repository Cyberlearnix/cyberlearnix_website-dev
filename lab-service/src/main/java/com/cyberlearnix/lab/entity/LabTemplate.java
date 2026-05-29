package com.cyberlearnix.lab.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "lab_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String dockerImage;

    /** CPU limit in cores (e.g. 0.5 = half a core) */
    private Double cpuLimit;

    /** Memory limit in bytes (e.g. 536870912 = 512 MB) */
    private Long memoryLimit;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Comma-separated list of pre-installed tools */
    @Column(columnDefinition = "TEXT")
    private String toolsList;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
