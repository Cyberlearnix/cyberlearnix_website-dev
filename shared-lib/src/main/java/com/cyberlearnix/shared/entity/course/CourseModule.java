package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "course_modules")
public class CourseModule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // Self-referencing: null = top-level chapter, set = sub-chapter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_module_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private CourseModule parentModule;

    @OneToMany(mappedBy = "parentModule", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonManagedReference("submodule-ref")
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<CourseModule> subModules;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "order_index")
    private Integer orderIndex = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonManagedReference
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<ModuleContent> contents;

    // Manual setters for Lombok compatibility
    public void setActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getActive() {
        return isActive;
    }
}
