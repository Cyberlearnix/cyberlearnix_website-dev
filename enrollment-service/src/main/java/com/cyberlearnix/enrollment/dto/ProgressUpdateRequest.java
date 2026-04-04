package com.cyberlearnix.enrollment.dto;

import lombok.Data;

@Data
public class ProgressUpdateRequest {
    private Integer progress;
    private String completedAt; // ISO format string
}
