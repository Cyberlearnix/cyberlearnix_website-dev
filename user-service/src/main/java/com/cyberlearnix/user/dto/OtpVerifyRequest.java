package com.cyberlearnix.user.dto;

import lombok.Data;

@Data
public class  OtpVerifyRequest {
    private String email;
    private String otp;
    private String sessionId;
    private String newPassword;
}
