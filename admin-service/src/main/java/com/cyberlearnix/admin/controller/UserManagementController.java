package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserServiceClient userServiceClient;

    @GetMapping
    public List<Map<String, Object>> getAllUsers(@RequestParam(required = false) String role,
                                                  @RequestHeader("Authorization") String auth) {
        return userServiceClient.getAllUsers(role, auth);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserDetails(@PathVariable String id,
                                                               @RequestHeader("Authorization") String auth) {
        try {
            return ResponseEntity.ok(userServiceClient.getUserById(id, auth));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable String id,
                                               @RequestBody Map<String, Boolean> statusRequest,
                                               @RequestHeader("Authorization") String auth) {
        if (statusRequest.get("isActive") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "isActive field is required"));
        }
        return ResponseEntity.ok(userServiceClient.updateUserStatus(id, statusRequest, auth));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id,
                                         @RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(userServiceClient.deleteUser(id, auth));
    }

    @GetMapping("/instructors")
    public List<Map<String, Object>> getAllInstructors(@RequestHeader("Authorization") String auth) {
        return userServiceClient.getAllUsers("teacher", auth);
    }
}

