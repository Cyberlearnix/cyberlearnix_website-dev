package com.cyberlearnix.form.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponseDTO {
    private Long id;
    private String formId;
    private String userEmail;
    private Double score;
    private Object submissionData;
    private LocalDateTime createdAt;
}
