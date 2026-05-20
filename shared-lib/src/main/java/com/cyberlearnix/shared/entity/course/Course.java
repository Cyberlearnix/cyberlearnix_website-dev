package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "courses")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"modules", "courseModules"})
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private String category;

    @Column(name = "difficulty_level")
    private String difficultyLevel; // BEGINNER, INTERMEDIATE, ADVANCED

    private String duration; // e.g. "12h 30m"

    @Column(name = "content_url")
    private String contentUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "base_price")
    private Double basePrice;

    @Column(name = "gst_percent")
    private Integer gstPercent;

    @Column(name = "final_price")
    private Double finalPrice;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean isActive = true;

    @Column(name = "certificate_enabled", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean certificateEnabled = false;

    @Column(name = "instructor_name")
    private String instructorName;

    @Column(name = "certificate_image_url", length = 1000)
    private String certificateImageUrl;

    @Column(name = "created_by")
    private String createdBy; // Reference to user_profiles.id

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "status")
    private String status = "APPROVED"; // PENDING, APPROVED, PUBLISHED, TRASHED

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    @PreUpdate
    public void calculatePricing() {
        if (this.basePrice != null) {
            if (this.gstPercent == null) {
                this.gstPercent = 18; // Default to 18%
            }
            double gstAmount = (this.basePrice * this.gstPercent) / 100.0;
            this.finalPrice = Math.round((this.basePrice + gstAmount) * 100.0) / 100.0;
        }
        this.updatedAt = LocalDateTime.now();
    }

    // Manual setter for Lombok compatibility
    public void setActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getActive() {
        return isActive;
    }
}
