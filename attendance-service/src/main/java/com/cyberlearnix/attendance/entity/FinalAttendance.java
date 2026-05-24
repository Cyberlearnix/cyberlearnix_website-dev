package com.cyberlearnix.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Computed final attendance record per student per meeting.
 * Recalculated after meeting ends or on admin action.
 */
@Data
@Entity
@Table(name = "final_attendance", indexes = {
    @Index(name = "idx_fa_meeting_id", columnList = "meetingId"),
    @Index(name = "idx_fa_student_id", columnList = "studentId"),
    @Index(name = "idx_fa_meeting_student", columnList = "meetingId,studentId", unique = true),
    @Index(name = "idx_fa_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
public class FinalAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "meeting_id", nullable = false)
    private String meetingId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "student_email")
    private String studentEmail;

    /** Total active seconds (sum of all join-leave cycles) */
    @Column(name = "total_active_seconds")
    private Long totalActiveSeconds = 0L;

    /** Meeting total duration in seconds */
    @Column(name = "meeting_duration_seconds")
    private Long meetingDurationSeconds = 0L;

    /** Computed percentage: totalActiveSeconds / meetingDurationSeconds * 100 */
    @Column(name = "attendance_percentage")
    private Double attendancePercentage = 0.0;

    /** PRESENT | PARTIAL | LATE | ABSENT | EXCUSED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceStatus status = AttendanceStatus.ABSENT;

    /** Number of join-rejoin cycles */
    @Column(name = "rejoin_count")
    private Integer rejoinCount = 0;

    /** Whether student joined late (after threshold) */
    @Column(name = "is_late")
    private Boolean late = false;

    /** Minutes late (if joined after scheduled start + threshold) */
    @Column(name = "late_by_minutes")
    private Integer lateByMinutes = 0;

    /** Whether this record has been manually overridden by admin */
    @Column(name = "is_overridden")
    private Boolean overridden = false;

    @Column(name = "override_by")
    private String overrideBy;

    @Column(name = "override_at")
    private LocalDateTime overrideAt;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    /** Whether attendance is locked (admin locked — no more auto updates) */
    @Column(name = "is_locked")
    private Boolean locked = false;

    /** Certificate eligibility contribution */
    @Column(name = "counts_for_certificate")
    private Boolean countsForCertificate = false;

    /** Admin notes */
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum AttendanceStatus {
        PRESENT, PARTIAL, LATE, ABSENT, EXCUSED
    }
}
