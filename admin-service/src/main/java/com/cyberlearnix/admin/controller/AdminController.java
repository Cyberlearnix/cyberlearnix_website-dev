package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.client.AuthServiceClient;
import com.cyberlearnix.admin.client.UserServiceClient;
import com.cyberlearnix.admin.dto.AdminLoginRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminController {

    private final AuthServiceClient authServiceClient;
    private final UserServiceClient userServiceClient;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AdminLoginRequest loginRequest) {
        try {
            Map<String, Object> response = authServiceClient.login(Map.of("email", loginRequest.getEmail(), "password", loginRequest.getPassword()));
            Map<String, Object> userMap = (Map<String, Object>) response.get("user");
            String role = (String) userMap.get("role");

            if (!"admin".equalsIgnoreCase(role)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied: Not an administrator"));
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid admin credentials"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> logoutRequest,
                                     @RequestHeader("Authorization") String authHeader) {
        authServiceClient.logout(logoutRequest, authHeader);
        return ResponseEntity.ok(Map.of("message", "Admin logged out successfully"));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String authHeader) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String adminId = auth.getPrincipal().toString();
        try {
            return ResponseEntity.ok(userServiceClient.getUserById(adminId, authHeader));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> profileRequest,
                                            @RequestHeader("Authorization") String authHeader) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String adminId = auth.getPrincipal().toString();
        try {
            return ResponseEntity.ok(userServiceClient.updateUser(adminId, profileRequest, authHeader));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}

