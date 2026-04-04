package com.cyberlearnix.shared.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "enrollment_forms_config")
public class EnrollmentFormConfig {
    @Id
    private String id;

    private String title;
    private String description;

    @Column(name = "course_id")
    private Long courseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String fields;

    @Column(name = "is_active")
    private boolean isActive = true;

    private String token;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "is_quiz")
    private boolean isQuiz = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quiz_settings", columnDefinition = "jsonb")
    private String quizSettings;

    @Column(name = "limit_one_response")
    private boolean limitOneResponse = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
