package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "live_session_contents")
@PrimaryKeyJoinColumn(name = "content_id")
public class LiveSessionContent extends ModuleContent {

    @Column(name = "platform")
    private String platform; // ZOOM, GOOGLE_MEET, MICROSOFT_TEAMS, etc.

    @Column(name = "meeting_url")
    private String meetingUrl;

    @Column(name = "meeting_id")
    private String meetingId;

    @Column(name = "meeting_password")
    private String meetingPassword;

    @Column(name = "agenda", columnDefinition = "TEXT")
    private String agenda;

    @Column(name = "record_session")
    private Boolean recordSession = true;

    @Column(name = "session_at")
    private LocalDateTime sessionAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;
}
