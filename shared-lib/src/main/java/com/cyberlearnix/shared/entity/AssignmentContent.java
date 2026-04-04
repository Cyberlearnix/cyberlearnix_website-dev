package com.cyberlearnix.shared.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper=false)
@Entity
@Table(name = "assignment_contents")
@PrimaryKeyJoinColumn(name = "content_id")
public class AssignmentContent extends ModuleContent {
    
    @Column(name = "assignment_type")
    private String assignmentType; // PROJECT, CASE_STUDY, RESEARCH, CODING
    
    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;
    
    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirements;
    
    @Column(name = "submission_format")
    private String submissionFormat; // PDF, CODE, VIDEO, LINK
    
    @Column(name = "max_score")
    private Integer maxScore = 100;
    
    @Column(name = "due_date")
    private LocalDateTime dueDate;
    
    @Column(name = "late_submission_allowed")
    private Boolean lateSubmissionAllowed = false;
    
    @Column(name = "late_penalty_percent")
    private Integer latePenaltyPercent = 10;
    
    @Column(name = "rubric", columnDefinition = "TEXT")
    private String rubric; // JSON rubric for grading
    
    @Column(name = "auto_grade")
    private Boolean autoGrade = false;
    
    @Column(name = "plagiarism_check")
    private Boolean plagiarismCheck = true;
}
