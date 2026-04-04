package com.cyberlearnix.form.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormResponseDTO {
    private String id;
    private String title;
    private String description;
    private Object fields;
    private boolean isActive;
    private String token;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean isQuiz;
    private Object quizSettings;
    private boolean limitOneResponse;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
