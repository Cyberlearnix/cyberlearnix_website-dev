package com.cyberlearnix.lab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTemplateRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "dockerImage is required")
    private String dockerImage;

    /** CPU limit in cores (e.g. 0.5). Falls back to lab.defaults.default-cpu if null. */
    private Double cpuLimit;

    /** Memory limit in bytes (e.g. 536870912 = 512 MB). Falls back to lab.defaults.default-memory if null. */
    private Long memoryLimit;

    private String description;

    private String toolsList;
}
