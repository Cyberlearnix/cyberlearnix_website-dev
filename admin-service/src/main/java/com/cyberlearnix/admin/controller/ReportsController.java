package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.client.CourseServiceClient;
import com.cyberlearnix.admin.client.EnrollmentServiceClient;
import com.cyberlearnix.admin.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final UserServiceClient userServiceClient;
    private final CourseServiceClient courseServiceClient;
    private final EnrollmentServiceClient enrollmentServiceClient;

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUserStats(@RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(userServiceClient.getUserStats(auth));
    }

    @GetMapping("/courses")
    public ResponseEntity<Map<String, Object>> getCourseStats(@RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(courseServiceClient.getCourseStats(auth));
    }

    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> getRevenueStats(@RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(enrollmentServiceClient.getRevenueStats(auth));
    }
}
