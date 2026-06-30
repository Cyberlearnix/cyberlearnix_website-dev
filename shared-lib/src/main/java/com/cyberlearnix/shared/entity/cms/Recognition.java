package com.cyberlearnix.shared.entity.cms;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "recognitions")
public class Recognition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String authority;

    @Column(name = "certificate_no")
    private String certificateNo;

    @Column(name = "valid_until")
    private String validUntil;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "verify_url")
    private String verifyUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
