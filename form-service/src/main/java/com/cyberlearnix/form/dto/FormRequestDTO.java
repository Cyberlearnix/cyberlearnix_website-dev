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
public class FormRequestDTO {
    private String title;
    private String description;
    private Object fields;
    @Builder.Default
    private boolean isActive = true;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean isQuiz;
    private Object quizSettings;
    private boolean limitOneResponse;
    private boolean paymentEnabled;
    private Long courseId;
    private Double paymentAmount;
    private Integer gstPercent;
    private Double gstAmount;
    private Double totalAmount;
}
