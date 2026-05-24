package com.cyberlearnix.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Certificate eligibility per student per course/batch.
 * Recalculated when attendance changes.
 */
@Data
@Entity
@Table(name = "certificate_eligibility", indexes = {
    @Index(name = "idx_ce_student_course", columnList = "studentId,courseId", unique = true),
    @Index(name = "idx_ce_student_id", columnList = "studentId"),
    @Index(name = "idx_ce_eligible", columnList = "eligible")
})
@EntityListeners(AuditingEntityListener.class)
public class CertificateEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "student_email")
    private String studentEmail;

    @Column(name = "course_id", nullable = false)
    private String courseId;

    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "total_meetings")
    private Integer totalMeetings = 0;

    @Column(name = "attended_meetings")
    private Integer attendedMeetings = 0;

    @Column(name = "mandatory_meetings")
    private Integer mandatoryMeetings = 0;

    @Column(name = "mandatory_attended")
    private Integer mandatoryAttended = 0;

    @Column(name = "overall_attendance_percentage")
    private Double overallAttendancePercentage = 0.0;

    /** Whether student meets the minimum attendance requirement */
    @Column(name = "meets_attendance_requirement")
    private Boolean meetsAttendanceRequirement = false;

    /** Final eligibility flag */
    @Column(name = "eligible")
    private Boolean eligible = false;

    @Column(name = "ineligibility_reason")
    private String ineligibilityReason;

    @Column(name = "certificate_issued")
    private Boolean certificateIssued = false;

    @Column(name = "certificate_issued_at")
    private LocalDateTime certificateIssuedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
