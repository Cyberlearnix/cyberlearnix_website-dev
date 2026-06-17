package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.identity.Member;
import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.identity.MemberRepository;
import com.cyberlearnix.shared.repository.user.UserProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * Public endpoint — no authentication required.
 * Called when a member's secure QR code is scanned or ID typed.
 */
@RestController
@RequestMapping("/api/public")
public class PublicVerifyController {

    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private MemberRepository memberRepository;

    @GetMapping("/verify/{enrollmentNumber}")
    public ResponseEntity<?> verifyMember(@PathVariable String enrollmentNumber) {
        if (enrollmentNumber == null || enrollmentNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid enrollment or identity number"));
        }

        String searchId = enrollmentNumber.toUpperCase().trim();

        // 1. Try finding in the new centralized Members directory
        Optional<Member> memberOpt = memberRepository.findByMemberId(searchId);
        if (memberOpt.isPresent()) {
            Member m = memberOpt.get();
            if (!Boolean.TRUE.equals(m.getIsActive())) {
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Account inactive",
                    "message", "This member profile is no longer active"
                ));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("valid", true);
            response.put("id", m.getId());
            response.put("enrollmentNumber", m.getMemberId());
            response.put("fullName", m.getFullName());
            response.put("email", m.getEmail());
            response.put("role", m.getMemberType().toLowerCase());
            response.put("photoUrl", m.getProfilePhoto());
            
            String locationStr = "CyberLearnix";
            if (m.getDepartment() != null && !m.getDepartment().isBlank()) {
                locationStr = m.getDepartment();
                if (m.getDesignation() != null && !m.getDesignation().isBlank()) {
                    locationStr += " / " + m.getDesignation();
                }
            } else if (m.getDesignation() != null && !m.getDesignation().isBlank()) {
                locationStr = m.getDesignation();
            }
            response.put("location", locationStr);
            response.put("enrolledAt", m.getJoiningDate() != null ? m.getJoiningDate().toString() : m.getCreatedAt().toString());
            response.put("institute", "Cyberlearnix Private Limited");
            return ResponseEntity.ok(response);
        }

        // 2. Fallback to legacy UserProfile table for backwards compatibility
        Optional<UserProfile> profileOpt = userProfileRepository.findByEnrollmentNumber(searchId);
        if (profileOpt.isPresent()) {
            UserProfile p = profileOpt.get();
            if (!Boolean.TRUE.equals(p.getIsActive())) {
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Account inactive",
                    "message", "This student account is no longer active"
                ));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("valid", true);
            response.put("id", p.getId());
            response.put("enrollmentNumber", p.getEnrollmentNumber());
            response.put("fullName", p.getFullName() != null ? p.getFullName() : "Student");
            response.put("email", p.getEmail());
            response.put("role", p.getRole() != null ? p.getRole() : "student");
            response.put("photoUrl", p.getPhotoUrl());
            response.put("location", p.getLocation() != null ? p.getLocation() : "Remote / Online");
            response.put("enrolledAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
            response.put("institute", "Cyberlearnix Private Limited");
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(404).body(Map.of(
            "error", "Identity not found",
            "message", "No active profile matches this identity or enrollment number"
        ));
    }
}
