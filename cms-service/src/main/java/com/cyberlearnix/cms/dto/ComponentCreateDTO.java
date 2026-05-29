package com.cyberlearnix.cms.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ComponentCreateDTO {
    private String componentType;
    private Map<String, Object> componentData;
    private Integer orderIndex;
}
