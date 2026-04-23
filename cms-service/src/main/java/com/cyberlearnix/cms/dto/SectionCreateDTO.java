package com.cyberlearnix.cms.dto;

import com.cyberlearnix.shared.entity.cms.SectionLayoutType;
import lombok.Data;

@Data
public class SectionCreateDTO {
    private SectionLayoutType layoutType;
    private Integer orderIndex;
}
