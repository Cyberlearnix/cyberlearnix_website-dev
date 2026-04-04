package com.cyberlearnix.course.dto;

import lombok.Data;
import java.util.List;

@Data
public class QuizQuestionDTO {
    private String questionText;
    private String questionType; // SINGLE_CHOICE, MULTIPLE_CHOICE, etc.
    private Integer points;
    private String explanation;
    private List<QuestionOptionDTO> options;
}
