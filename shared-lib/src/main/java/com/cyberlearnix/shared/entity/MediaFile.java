package com.cyberlearnix.shared.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "media_files")
public class MediaFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;
    private String url;
    private String type;
    private Long size;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
