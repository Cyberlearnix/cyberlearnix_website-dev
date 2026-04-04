package com.cyberlearnix.shared.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "shop_settings")
public class ShopSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Boolean enabled = false;

    @Column(name = "shop_url")
    private String shopUrl;

    @Column(name = "announcement_text")
    private String announcementText;

    private String name;
    private String description;
    private String currency = "USD";
    private String theme = "light";

    @Column(columnDefinition = "TEXT")
    private String products = "[]";
}
