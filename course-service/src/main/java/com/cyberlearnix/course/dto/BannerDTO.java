package com.cyberlearnix.course.dto;

import lombok.Data;

@Data
public class BannerDTO {
    private String title;
    private String subtitle;
    private String imgUrl;
    private String buttons; // JSON string in entity, but we keep it simple for now
    private Integer displayOrder;
}
