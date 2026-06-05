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

    /** Optional: the course this lab assignment belongs to. When set, the lab
     *  will appear in the student's per-course lab view. */
    private Long courseId;
}
