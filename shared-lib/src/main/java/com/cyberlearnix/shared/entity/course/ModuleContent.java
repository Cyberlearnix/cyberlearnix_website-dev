package com.cyberlearnix.shared.entity.course;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "module_contents")
@Inheritance(strategy = InheritanceType.JOINED)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "contentType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = LabContent.class, name = "LAB"),
        @JsonSubTypes.Type(value = AssignmentContent.class, name = "ASSIGNMENT"),
        @JsonSubTypes.Type(value = LectureContent.class, name = "LECTURE"),
        @JsonSubTypes.Type(value = LectureContent.class, name = "VIDEO"),
        @JsonSubTypes.Type(value = LectureContent.class, name = "IMAGE"),
        @JsonSubTypes.Type(value = LectureContent.class, name = "TEXT"),
        @JsonSubTypes.Type(value = QuizContent.class, name = "QUIZ"),
        @JsonSubTypes.Type(value = QuizContent.class, name = "EXAM"),
        @JsonSubTypes.Type(value = LiveSessionContent.class, name = "LIVE")
})
public abstract class ModuleContent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "module_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonBackReference
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private CourseModule module;

    @Column(name = "module_id", insertable = false, updatable = false)
    private Long moduleId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "content_type", nullable = false)
    private String contentType; // LECTURE, LAB, ASSIGNMENT, QUIZ, VIDEO

    @Column(name = "order_index")
    private Integer orderIndex = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "status")
    private String status = "PENDING";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "is_free_preview")
    private Boolean isFreePreview = false;

    @Column(name = "requires_enrollment")
    private Boolean requiresEnrollment = true;

    @Column(name = "visibility")
    private String visibility = "ENROLLED";

    @Column(name = "available_from")
    private LocalDateTime availableFrom;

    @Column(name = "available_until")
    private LocalDateTime availableUntil;

    @Column(name = "drip")
    private Boolean drip = false;

    @Column(name = "drip_days")
    private Integer dripDays;

    @Column(name = "mandatory")
    private Boolean mandatory = true;

    @Column(name = "completion_type")
    private String completionType = "VIEW";

    @Column(name = "watch_percent")
    private Integer watchPercent = 80;

    @Column(name = "pass_percent")
    private Integer passPercent = 70;

    @Column(name = "slug")
    private String slug;

    @Column(name = "meta_desc", columnDefinition = "TEXT")
    private String metaDesc;

    @Column(name = "tags")
    private String tags;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "has_prerequisite")
    private Boolean hasPrerequisite = false;

    @Column(name = "prerequisite_id")
    private String prerequisiteId;

    // Manual setters for Lombok compatibility
    public void setActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getActive() {
        return isActive;
    }
}
