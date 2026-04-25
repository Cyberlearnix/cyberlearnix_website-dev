package com.cyberlearnix.instructor.controller;

import com.cyberlearnix.instructor.client.CourseServiceClient;
import com.cyberlearnix.instructor.client.EnrollmentServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/instructor/analytics")
@RequiredArgsConstructor
public class InstructorAnalyticsController {

    private final CourseServiceClient courseServiceClient;
    private final EnrollmentServiceClient enrollmentServiceClient;

    /**
     * GET /api/instructor/analytics
     * Returns aggregated analytics for the authenticated instructor's courses.
     * This is a future-scope endpoint — data will expand as course rating and
     * progress tracking features are implemented.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getAnalytics(
            @RequestHeader("Authorization") String auth,
            Authentication authentication) {

        String instructorId = authentication.getName();

        List<Map<String, Object>> allCourses = courseServiceClient.getAllCourses(null, auth);
        long totalCourses = allCourses.stream()
                .filter(c -> instructorId.equals(String.valueOf(c.get("createdBy"))))
                .count();
        long publishedCourses = allCourses.stream()
                .filter(c -> instructorId.equals(String.valueOf(c.get("createdBy"))))
                .filter(c -> "PUBLISHED".equalsIgnoreCase(String.valueOf(c.get("status"))))
                .count();

        Map<String, Object> courseStats = courseServiceClient.getCourseStats(auth);
        Map<String, Object> enrollmentStats = enrollmentServiceClient.getEnrollmentStats(auth);

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("instructorId", instructorId);
        analytics.put("totalCourses", totalCourses);
        analytics.put("publishedCourses", publishedCourses);
        analytics.put("courseStats", courseStats);
        analytics.put("enrollmentStats", enrollmentStats);

        return ResponseEntity.ok(analytics);
    }
}
