package com.cyberlearnix.user.dto;

import lombok.Data;

@Data
public class TokenPasswordResetRequest {
    private String token;
    private String newPassword;
    private String confirmPassword;
}
