package com.cyberlearnix.course.dto;

import lombok.Data;

@Data
public class ContentCreateDTO {
    private String title;
    private String description;
    private String contentType; // LAB, ASSIGNMENT, LECTURE, VIDEO, QUIZ, EXAM
    private Integer orderIndex;
    
    // Additional fields for specific types
    private String labType;
    private String instructions;
    private String environmentConfig;
    private Integer durationMinutes;
    
    private String assignmentType;
    private Integer maxScore;
    
    private String videoUrl;
    private String contentText;
    private Boolean isPreview;
    private String attachmentUrl;
    
    private String quizId;
    private Integer timeLimitMinutes;
    private Integer passingScore;
    private Integer maxAttempts;
}
