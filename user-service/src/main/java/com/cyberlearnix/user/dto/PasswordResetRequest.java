package com.cyberlearnix.user.dto;

import lombok.Data;

@Data
public class PasswordResetRequest {
    private String newPassword;
    private String confirmPassword;
}
