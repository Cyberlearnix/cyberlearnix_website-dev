package com.cyberlearnix.lab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CourseLabConfigRequest {

    @NotNull(message = "courseId is required")
    private Long courseId;

    @NotNull(message = "templateId is required")
    private Long templateId;

    private Boolean requiresApproval = true;
}
