package com.cyberlearnix.course.dto;

import lombok.Data;

@Data
public class CourseCreateDTO {
    private String title;
    private String description;
    private String category;
    private String difficultyLevel;
    private String duration;
    private String contentUrl;
    private String thumbnailUrl;
    private Double basePrice;
    private Integer gstPercent;
    private Double finalPrice;
    private Boolean isActive;
    private Boolean certificateEnabled;
    private String instructorName;
    private String certificateImageUrl;
}
