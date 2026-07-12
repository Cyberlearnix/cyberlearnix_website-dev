package com.cyberlearnix.admin.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "meeting_attendances",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_meeting_student",
           columnNames = {"meeting_id", "student_id"}
       ))
public class MeetingAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private String meetingId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "join_time")
    private LocalDateTime joinTime;

    @Column(name = "leave_time")
    private LocalDateTime leaveTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "status")
    private String status = "PRESENT";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
