package com.cyberlearnix.user.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class BusinessEnquiryRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
    private String fullName;

    @Size(max = 200, message = "Organization name must not exceed 200 characters")
    private String organizationName;

    @NotBlank(message = "Email address is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 255, message = "Email address must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^\\+?[0-9\\s\\-()]{7,25}$", message = "Please provide a valid mobile number with country code")
    private String mobileNumber;

    @NotBlank(message = "City / Location is required")
    @Size(min = 2, max = 120, message = "City / Location must be between 2 and 120 characters")
    private String city;

    @Size(max = 100, message = "Designation must not exceed 100 characters")
    private String designation;

    @Size(max = 100, message = "Enquiry category must not exceed 100 characters")
    private String enquiryType;

    @NotEmpty(message = "Please select at least one service or enquiry category")
    @Size(max = 25, message = "Too many services selected")
    private List<@NotBlank String> selectedServices;

    @NotBlank(message = "Please describe your requirements")
    @Size(min = 5, max = 5000, message = "Requirements must be between 5 and 5000 characters")
    private String requirements;

    @Size(max = 80)
    private String estimatedBudget;

    @Size(max = 80)
    private String expectedTimeline;

    @Size(max = 80)
    private String preferredMeeting;

    // University & College Collaboration conditional fields
    @Size(max = 200)
    private String institutionName;

    @Size(max = 120)
    private String institutionType;

    @Size(max = 150)
    private String department;

    @Size(max = 50)
    private String studentCount;

    private List<String> collaborationServices;

    // Section Five: Final details
    @Size(max = 40)
    private String preferredContactMethod;

    @Size(max = 80)
    private String marketingSource;

    @NotNull(message = "Privacy Policy consent is required")
    @AssertTrue(message = "You must agree to the Privacy Policy")
    private Boolean privacyConsent;

    @NotNull(message = "Contact consent is required")
    @AssertTrue(message = "You must consent to Cyberlearnix contacting you regarding your enquiry")
    private Boolean contactConsent;

    // UTM tracking parameters
    @Size(max = 120)
    private String utmSource;

    @Size(max = 120)
    private String utmMedium;

    @Size(max = 150)
    private String utmCampaign;

    @Size(max = 150)
    private String utmContent;

    // Security / Anti-spam fields
    private String captchaToken;

    // Honeypot field (must remain empty for legitimate users)
    private String websiteUrl;
}
