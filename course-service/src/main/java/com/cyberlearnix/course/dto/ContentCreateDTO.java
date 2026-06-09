package com.cyberlearnix.course.dto;

import lombok.Data;

@Data
public class ContentCreateDTO {
    private String title;
    private String description;
    private String contentType; // LAB, ASSIGNMENT, LECTURE, VIDEO, IMAGE, TEXT, ARTICLE, QUIZ, EXAM
    private Integer orderIndex;
    
    // Additional fields for specific types
    private String labType;
    private String instructions;
    private String environmentConfig;
    private Integer durationMinutes;
    
    private String assignmentType;
    private Integer maxScore;
    private String assignmentMetadata;
    
    private String videoUrl;
    private String videoWidth;
    private String videoHeight;
    private String videoFrameHtml;
    private String contentText;
    private Boolean isPreview;
    private String attachmentUrl;

    // IMAGE type: image URL + optional metadata
    private String imageUrl;
    private String caption;
    private String altText;

    private String contentBlocks;
    
    private String quizId;
    private Integer timeLimitMinutes;
    private Integer passingScore;
    private Integer maxAttempts;

    // LIVE type
    private String platform;
    private String meetingUrl;
    private String meetingId;
    private String meetingPassword;
    private String agenda;
    private Boolean recordSession;
    private String sessionAt; // ISO String

    private String status;
    private String scheduledAt;
    private Boolean isFreePreview;
    private Boolean requiresEnrollment;
    private String visibility;
    private String availableFrom;
    private String availableUntil;
    private Boolean drip;
    private Integer dripDays;
    private Boolean mandatory;
    private String completionType;
    private Integer watchPercent;
    private Integer passPercent;
    private String slug;
    private String metaDesc;
    private String tags;
    private Integer estimatedMinutes;
    private Boolean hasPrerequisite;
    private String prerequisiteId;
}
