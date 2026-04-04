package com.cyberlearnix.enrollment.dto;

import lombok.Data;

@Data
public class PaymentVerificationRequest {
    private Long enrollmentId;
    private String action; // APPROVE, REJECT
    private String rejectionReason;
}
