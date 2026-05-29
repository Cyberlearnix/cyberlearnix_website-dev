package com.cyberlearnix.user.dto;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private String fullName;
    private String phone;
    private String photoUrl;
    private String email;
    private String age;
    private String bio;
    private String location;
    private String dateOfBirth;
    
    // Support for alternative naming in legacy payloads
    private String full_name;
    private String phone_number;
    private String avatar_url;
}
