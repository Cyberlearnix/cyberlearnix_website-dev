package com.cyberlearnix.form.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class FormRequestDTO {
    private String title;
    private String description;
    private Object fields;
    @Builder.Default
    @JsonProperty("isActive")
    private boolean isActive = true;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @JsonProperty("isQuiz")
    private boolean isQuiz;
    private Object quizSettings;
    private boolean limitOneResponse;
    private boolean paymentEnabled;
    private Long courseId;
    private Double paymentAmount;
    private Integer gstPercent;
    private Double gstAmount;
    private Double totalAmount;

    public FormRequestDTO() {
        this.isActive = true;
    }

    @JsonProperty("isActive")
    public boolean isActive() {
        return isActive;
    }

    @JsonProperty("isActive")
    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    @JsonProperty("isQuiz")
    public boolean isQuiz() {
        return isQuiz;
    }

    @JsonProperty("isQuiz")
    public void setQuiz(boolean isQuiz) {
        this.isQuiz = isQuiz;
    }
}
