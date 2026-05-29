package com.cyberlearnix.form.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;
import java.util.List;

@Data
@Builder
public class FormAnalyticsDTO {
    private String formId;
    private long totalResponses;
    private Double averageScore; // Null if not a quiz
    private List<QuestionAnalytics> questions;

    @Data
    @Builder
    public static class QuestionAnalytics {
        private String label;
        private String fieldType;
        private Map<String, Long> optionCounts; // For multiple choice
        private List<String> recentAnswers; // For text/other fields
    }
}
