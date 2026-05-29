package com.cyberlearnix.user.dto;

import lombok.Data;

@Data
public class UserCreateRequest {
    private String fullName;
    private String email;
    private String phone;
    private String role;
}
