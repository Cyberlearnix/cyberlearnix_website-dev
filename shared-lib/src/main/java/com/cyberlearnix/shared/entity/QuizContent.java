package com.cyberlearnix.shared.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "quiz_contents")
@PrimaryKeyJoinColumn(name = "content_id")
public class QuizContent extends ModuleContent {

    @Column(name = "quiz_id")
    private String quizId; // Reference to a quiz configuration

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "passing_score")
    private Integer passingScore = 70;

    @Column(name = "max_attempts")
    private Integer maxAttempts = 3;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonManagedReference
    private java.util.List<QuizQuestion> questions;
}
