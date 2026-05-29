package com.cyberlearnix.course.dto;

import lombok.Data;

@Data
public class ModuleCreateDTO {
    private String title;
    private String description;
    private String imageUrl;
    private Integer orderIndex;
    // Set to create a sub-chapter under a parent chapter
    private Long parentModuleId;
}
