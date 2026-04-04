package com.cyberlearnix.course.dto;

import lombok.Data;

@Data
public class ModuleCreateDTO {
    private String title;
    private String description;
    private Integer orderIndex;
}
