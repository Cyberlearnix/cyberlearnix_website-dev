package com.cyberlearnix.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Audit trail for every admin-initiated attendance change.
 */
@Data
@Entity
@Table(name = "attendance_overrides", indexes = {
    @Index(name = "idx_ao_meeting_id", columnList = "meetingId"),
    @Index(name = "idx_ao_student_id", columnList = "studentId"),
    @Index(name = "idx_ao_admin_id", columnList = "adminId")
})
@EntityListeners(AuditingEntityListener.class)
public class AttendanceOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "final_attendance_id", nullable = false)
    private String finalAttendanceId;

    @Column(name = "meeting_id", nullable = false)
    private String meetingId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "admin_id", nullable = false)
    private String adminId;

    @Column(name = "admin_name")
    private String adminName;

    @Column(name = "previous_status")
    @Enumerated(EnumType.STRING)
    private FinalAttendance.AttendanceStatus previousStatus;

    @Column(name = "new_status")
    @Enumerated(EnumType.STRING)
    private FinalAttendance.AttendanceStatus newStatus;

    @Column(name = "previous_percentage")
    private Double previousPercentage;

    @Column(name = "new_percentage")
    private Double newPercentage;

    @Column(name = "previous_active_seconds")
    private Long previousActiveSeconds;

    @Column(name = "new_active_seconds")
    private Long newActiveSeconds;

    @Column(name = "action", nullable = false)
    private String action; // MARK_PRESENT | MARK_ABSENT | MARK_EXCUSED | EDIT_DURATION | ADD_MANUAL | LOCK | UNLOCK

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
