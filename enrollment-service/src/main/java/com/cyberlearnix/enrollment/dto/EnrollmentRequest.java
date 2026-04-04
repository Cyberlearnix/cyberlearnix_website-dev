package com.cyberlearnix.enrollment.dto;

import lombok.Data;

@Data
public class EnrollmentRequest {
    private String studentId;
    private Long courseId;
}
