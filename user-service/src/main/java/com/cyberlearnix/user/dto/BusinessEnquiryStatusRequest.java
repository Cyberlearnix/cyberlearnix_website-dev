package com.cyberlearnix.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessEnquiryStatusRequest {

    @NotBlank(message = "Lead status is required")
    @Size(max = 40)
    private String leadStatus;
}
