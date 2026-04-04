package com.cyberlearnix.shop.dto;

import lombok.Data;

@Data
public class ShopSettingsDTO {
    private Boolean enabled;
    private String shopUrl;
    private String announcementText;
    private String name;
    private String description;
    private String currency;
    private String theme;
}
