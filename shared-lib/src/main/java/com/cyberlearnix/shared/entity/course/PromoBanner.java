package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "promo_banners")
public class PromoBanner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "img_url")
    private String imgUrl;

    private String link;
    private String status = "active"; // active, inactive

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
