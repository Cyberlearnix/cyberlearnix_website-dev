package com.cyberlearnix.shared.entity.form;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "general_form_responses")
public class GeneralFormResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id")
    private String formId;

    @Column(name = "user_email")
    private String userEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submission_data", columnDefinition = "jsonb")
    private String submissionData;

    private Double score;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
