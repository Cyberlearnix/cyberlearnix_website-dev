package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalStudents", userRepository.countByRoleIgnoreCase("STUDENT"));
        stats.put("totalTeachers", userRepository.countByRoleIgnoreCase("TEACHER"));
        stats.put("totalAdmins", userRepository.countByRoleIgnoreCase("ADMIN"));
        return ResponseEntity.ok(stats);
    }
}
