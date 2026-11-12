package com.cyberlearnix.shared.entity.user;

import com.cyberlearnix.shared.converter.StringListJsonConverter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(
    name = "business_enquiries",
    indexes = {
        @Index(name = "idx_be_reference", columnList = "enquiry_reference", unique = true),
        @Index(name = "idx_be_email", columnList = "email"),
        @Index(name = "idx_be_enquiry_type", columnList = "enquiry_type"),
        @Index(name = "idx_be_lead_status", columnList = "lead_status"),
        @Index(name = "idx_be_created_at", columnList = "created_at"),
        @Index(name = "idx_be_submission_hash", columnList = "submission_hash")
    }
)
public class BusinessEnquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enquiry_reference", unique = true, nullable = false, length = 32)
    private String enquiryReference;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "organization_name", length = 200)
    private String organizationName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "mobile_number", nullable = false, length = 32)
    private String mobileNumber;

    @Column(name = "city", nullable = false, length = 120)
    private String city;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "enquiry_type", nullable = false, length = 100)
    private String enquiryType;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "selected_services", columnDefinition = "TEXT")
    private List<String> selectedServices = new ArrayList<>();

    @Column(name = "requirements", nullable = false, columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "estimated_budget", length = 80)
    private String estimatedBudget;

    @Column(name = "expected_timeline", length = 80)
    private String expectedTimeline;

    @Column(name = "preferred_meeting", length = 80)
    private String preferredMeeting;

    // University & College Collaboration fields
    @Column(name = "institution_type", length = 120)
    private String institutionType;

    @Column(name = "department", length = 150)
    private String department;

    @Column(name = "student_count", length = 50)
    private String studentCount;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "collaboration_services", columnDefinition = "TEXT")
    private List<String> collaborationServices = new ArrayList<>();

    // Final details & attribution
    @Column(name = "preferred_contact_method", length = 40)
    private String preferredContactMethod;

    @Column(name = "marketing_source", length = 80)
    private String marketingSource;

    @Column(name = "privacy_consent", nullable = false)
    private Boolean privacyConsent = Boolean.FALSE;

    @Column(name = "contact_consent", nullable = false)
    private Boolean contactConsent = Boolean.FALSE;

    // Lead management
    @Column(name = "lead_status", nullable = false, length = 40)
    private String leadStatus = "New";

    @Column(name = "assigned_to", length = 150)
    private String assignedTo;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "email_notification_status", nullable = false, length = 40)
    private String emailNotificationStatus = "PENDING";

    @Column(name = "email_delivery_details", columnDefinition = "TEXT")
    private String emailDeliveryDetails;

    // UTM Campaign Tracking
    @Column(name = "utm_source", length = 120)
    private String utmSource;

    @Column(name = "utm_medium", length = 120)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 150)
    private String utmCampaign;

    @Column(name = "utm_content", length = 150)
    private String utmContent;

    // Duplicate & security metadata (kept internal)
    @JsonIgnore
    @Column(name = "submission_hash", length = 64)
    private String submissionHash;

    @JsonIgnore
    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.leadStatus == null || this.leadStatus.isBlank()) this.leadStatus = "New";
        if (this.emailNotificationStatus == null || this.emailNotificationStatus.isBlank()) this.emailNotificationStatus = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
