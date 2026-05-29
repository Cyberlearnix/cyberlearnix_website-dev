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

    @Column(name = "video_width")
    private String videoWidth;

    @Column(name = "video_height")
    private String videoHeight;

    @Column(name = "video_frame_html", columnDefinition = "TEXT")
    private String videoFrameHtml;

    // For IMAGE content type: stores the image URL
    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    // Rich text blocks stored as JSON string.
    // Format: [{"type":"HEADING","level":1,"text":"..."},{"type":"SUBHEADING","text":"..."},
    //          {"type":"PARAGRAPH","text":"..."},{"type":"BULLET","items":["..."]},
    //          {"type":"IMAGE","url":"...","caption":"..."},{"type":"VIDEO","url":"..."}]
    @Column(name = "content_blocks", columnDefinition = "TEXT")
    private String contentBlocks;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "is_preview")
    private Boolean isPreview = false;

    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "interactive_url")
    private String interactiveUrl; // Support for Packet Tracer web, simulations, etc.

    // Manual setter for Lombok bool compatibility
    public void setIsPreview(Boolean isPreview) {
        this.isPreview = isPreview;
    }
}
