package com.cyberlearnix.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "meeting_participants", indexes = {
    @Index(name = "idx_participant_meeting", columnList = "meetingId"),
    @Index(name = "idx_participant_user", columnList = "userId")
})
@EntityListeners(AuditingEntityListener.class)
public class MeetingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "meeting_id", nullable = false)
    private String meetingId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String role;

    @CreatedDate
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;
}
