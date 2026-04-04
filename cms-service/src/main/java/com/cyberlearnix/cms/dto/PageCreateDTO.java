package com.cyberlearnix.cms.dto;

import lombok.Data;
import java.util.Map;

@Data
public class PageCreateDTO {
    private String title;
    private String slug;
    private String templateName;
    private Boolean isPublished;
    private String metaTitle;
    private String metaDescription;
    private Map<String, Object> metaKeywords;
}
