package com.cyberlearnix.shared.entity.identity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "members")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "member_id", unique = true, nullable = false)
    private String memberId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "member_type", nullable = false)
    private String memberType; // Student, Intern, Instructor, Mentor, Employee, HR, Team Lead, Manager, Director, CEO, Founder, Consultant, Guest Speaker, Partner

    private String department;

    private String designation;

    @Column(name = "profile_photo", columnDefinition = "TEXT")
    private String profilePhoto;

    @Column(nullable = false)
    private String status; // Pending, Approved, Rejected, ChangesRequested

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "verification_url", columnDefinition = "TEXT")
    private String verificationUrl;

    @Column(name = "qr_code_url", columnDefinition = "TEXT")
    private String qrCodeUrl;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_date")
    private LocalDateTime approvedDate;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
