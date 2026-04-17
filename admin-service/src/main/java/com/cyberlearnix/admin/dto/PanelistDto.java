package com.cyberlearnix.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PanelistDto {

    /** Optional display name of the invitee */
    private String name;

    /** Email address to send the Zoho meeting invitation to */
    @NotBlank(message = "Invitee email is required")
    @Email(message = "Invitee email must be valid")
    private String email;
}
