package com.cyberlearnix.admin.controller;

import com.cyberlearnix.shared.entity.User;
import com.cyberlearnix.shared.entity.UserProfile;
import com.cyberlearnix.shared.repository.UserProfileRepository;
import com.cyberlearnix.shared.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @GetMapping
    public List<User> getAllUsers(@RequestParam(required = false) String role) {
        if (role != null) {
            return userRepository.findAll().stream()
                    .filter(u -> u.getRole().equalsIgnoreCase(role))
                    .toList();
        }
        return userRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserDetails(@PathVariable String id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable String id, @RequestBody Map<String, Boolean> statusRequest) {
        Boolean isActive = statusRequest.get("isActive");
        if (isActive == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "isActive field is required"));
        }

        return userProfileRepository.findById(id)
                .map(profile -> {
                    profile.setIsActive(isActive);
                    profile.setUpdatedAt(LocalDateTime.now());
                    userProfileRepository.save(profile);
                    return ResponseEntity.ok(Map.of("message", "User status updated successfully", "isActive", isActive));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        userProfileRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    @GetMapping("/instructors")
    public List<User> getAllInstructors() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole().equalsIgnoreCase("teacher") || u.getRole().equalsIgnoreCase("instructor"))
                .toList();
    }
}
