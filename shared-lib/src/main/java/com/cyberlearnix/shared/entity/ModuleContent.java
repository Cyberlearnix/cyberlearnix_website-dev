package com.cyberlearnix.shared.entity;

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
        @JsonSubTypes.Type(value = QuizContent.class, name = "QUIZ"),
        @JsonSubTypes.Type(value = QuizContent.class, name = "EXAM")
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

    // Manual setters for Lombok compatibility
    public void setActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getActive() {
        return isActive;
    }
}
