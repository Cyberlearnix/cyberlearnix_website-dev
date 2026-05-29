package com.cyberlearnix.lab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LabRequestDto {

    @NotNull(message = "courseId is required")
    private Long courseId;

    @NotNull(message = "studentId is required")
    private String studentId;

    @NotNull(message = "templateId is required")
    private Long templateId;

    /** Optional note from the instructor */
    private String note;
}
