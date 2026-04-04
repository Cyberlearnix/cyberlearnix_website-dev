package com.cyberlearnix.shared.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper=false)
@Entity
@Table(name = "lab_contents")
@PrimaryKeyJoinColumn(name = "content_id")
public class LabContent extends ModuleContent {
    
    @Column(name = "lab_type")
    private String labType; // HANDS_ON, SIMULATION, VIRTUAL_LAB
    
    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;
    
    @Column(name = "environment_config", columnDefinition = "TEXT")
    private String environmentConfig; // JSON config for lab environment
    
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    
    @Column(name = "difficulty_level")
    private String difficultyLevel; // BEGINNER, INTERMEDIATE, ADVANCED
    
    @Column(name = "prerequisites", columnDefinition = "TEXT")
    private String prerequisites;
    
    @Column(name = "learning_objectives", columnDefinition = "TEXT")
    private String learningObjectives;
    
    @Column(name = "has_solution")
    private Boolean hasSolution = false;
    
    @Column(name = "solution_guide", columnDefinition = "TEXT")
    private String solutionGuide;
}
