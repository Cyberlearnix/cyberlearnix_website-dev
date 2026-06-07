package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.user.UserProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Public endpoint — no authentication required.
 * Called when a student's QR code is scanned. Returns safe public details.
 */
@RestController
@RequestMapping("/api/public")
public class PublicVerifyController {

    @Autowired private UserProfileRepository userProfileRepository;

    @GetMapping("/verify/{enrollmentNumber}")
    public ResponseEntity<?> verifyStudent(@PathVariable String enrollmentNumber) {
        if (enrollmentNumber == null || enrollmentNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid enrollment number"));
        }

        Optional<UserProfile> profileOpt = userProfileRepository
                .findByEnrollmentNumber(enrollmentNumber.toUpperCase().trim());

        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Enrollment not found",
                "message", "No student found with this enrollment number"
            ));
        }

        UserProfile p = profileOpt.get();
        if (!Boolean.TRUE.equals(p.getIsActive())) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Account inactive",
                "message", "This student account is no longer active"
            ));
        }

        // Return safe public fields — email included for QR verification display
        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("valid", true);
        response.put("enrollmentNumber", p.getEnrollmentNumber());
        response.put("fullName", p.getFullName() != null ? p.getFullName() : "Student");
        response.put("email", p.getEmail());
        response.put("role", p.getRole() != null ? p.getRole() : "student");
        response.put("photoUrl", p.getPhotoUrl());
        response.put("location", p.getLocation());
        response.put("enrolledAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
        response.put("institute", "Cyberlearnix Private Limited");
        return ResponseEntity.ok(response);
    }
}
