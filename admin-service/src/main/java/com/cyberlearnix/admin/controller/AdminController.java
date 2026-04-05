package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.client.AuthServiceClient;
import com.cyberlearnix.shared.entity.user.User;
import com.cyberlearnix.shared.repository.UserRepository;
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
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        try {
            Map<String, Object> response = authServiceClient.login(loginRequest);
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
    public ResponseEntity<?> logout(@RequestBody Map<String, String> logoutRequest, @RequestHeader("Authorization") String authHeader) {
        authServiceClient.logout(logoutRequest, authHeader);
        return ResponseEntity.ok(Map.of("message", "Admin logged out successfully"));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String adminId = auth.getPrincipal().toString();
        return userRepository.findById(adminId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> profileRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String adminId = auth.getPrincipal().toString();
        return userRepository.findById(adminId)
                .map(admin -> {
                    if (profileRequest.containsKey("email")) {
                        admin.setEmail(profileRequest.get("email"));
                    }
                    userRepository.save(admin);
                    return ResponseEntity.ok(admin);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
