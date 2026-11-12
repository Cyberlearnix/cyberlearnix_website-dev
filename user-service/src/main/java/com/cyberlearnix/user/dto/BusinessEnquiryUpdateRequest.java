package com.cyberlearnix.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessEnquiryUpdateRequest {

    @Size(max = 40)
    private String leadStatus;

    @Size(max = 150)
    private String assignedTo;

    @Size(max = 5000)
    private String adminNotes;
}
