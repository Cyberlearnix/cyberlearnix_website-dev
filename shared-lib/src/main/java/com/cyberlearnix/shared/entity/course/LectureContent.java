package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "lecture_contents")
@PrimaryKeyJoinColumn(name = "content_id")
public class LectureContent extends ModuleContent {

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "is_preview")
    private Boolean isPreview = false;

    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "interactive_url")
    private String interactiveUrl; // Support for Packet Tracer web, simulations, etc.
}
