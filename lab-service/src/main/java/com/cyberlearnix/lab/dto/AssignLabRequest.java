package com.cyberlearnix.lab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignLabRequest {

    @NotNull(message = "studentId is required")
    private String studentId;

    @NotNull(message = "templateId is required")
    private Long templateId;

    /** Optional: populated by gateway from JWT; fallback for direct calls */
    private String instructorId;
}
