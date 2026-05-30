package com.cyberlearnix.lab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalDecisionDto {

    @NotNull(message = "approved is required")
    private Boolean approved;

    /** Required when approved=false */
    private String rejectionReason;
}
