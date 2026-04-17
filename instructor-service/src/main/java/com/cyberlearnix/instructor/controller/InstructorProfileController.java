package com.cyberlearnix.instructor.controller;

import com.cyberlearnix.instructor.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/instructor/profile")
@RequiredArgsConstructor
public class InstructorProfileController {

    private final UserServiceClient userServiceClient;

    /**
     * GET /api/instructor/profile
     * Returns the authenticated instructor's profile from user-service.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestHeader("Authorization") String auth,
            Authentication authentication) {

        String instructorId = authentication.getName();
        Map<String, Object> profile = userServiceClient.getUserById(instructorId, auth);
        return ResponseEntity.ok(profile);
    }

    /**
     * PATCH /api/instructor/profile
     * Allows the instructor to update their own profile fields.
     */
    @PatchMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL')")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestBody Map<String, String> profileRequest,
            @RequestHeader("Authorization") String auth,
            Authentication authentication) {

        String instructorId = authentication.getName();
        Map<String, Object> updated = userServiceClient.updateUser(instructorId, profileRequest, auth);
        return ResponseEntity.ok(updated);
    }
}
