package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "course_assignments")
@Data
public class CourseAssignment {
    
    public enum AssignmentType {
        QUIZ,
        LAB,
        ASSIGNMENT
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "course_id")
    private Long courseId;
    
    @Column(name = "title")
    private String title;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "due_date")
    private LocalDateTime dueDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private AssignmentType type;
    
    @Column(name = "module_id")
    private Long moduleId;
    
    @Column(name = "content_id")
    private Long contentId;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
