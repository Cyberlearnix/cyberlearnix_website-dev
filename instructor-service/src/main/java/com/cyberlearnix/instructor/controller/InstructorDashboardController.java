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
@RequestMapping("/api/instructor")
@RequiredArgsConstructor
public class InstructorDashboardController {

    private final CourseServiceClient courseServiceClient;
    private final EnrollmentServiceClient enrollmentServiceClient;

    /**
     * GET /api/instructor/dashboard
     * Returns summary metrics for the authenticated instructor.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @RequestHeader("Authorization") String auth,
            Authentication authentication) {

        String instructorId = authentication.getName();

        // Fetch assigned courses from course-service
        List<Map<String, Object>> assignedCourses = courseServiceClient.getCourseTeachers(null, auth);
        long totalAssigned = assignedCourses.stream()
                .filter(entry -> instructorId.equals(String.valueOf(entry.get("teacherId"))))
                .count();

        // Fetch all courses created by this instructor
        List<Map<String, Object>> allCourses = courseServiceClient.getAllCourses(null, auth);
        long createdCourses = allCourses.stream()
                .filter(c -> instructorId.equals(String.valueOf(c.get("createdBy"))))
                .count();
        long publishedCourses = allCourses.stream()
                .filter(c -> instructorId.equals(String.valueOf(c.get("createdBy"))))
                .filter(c -> "PUBLISHED".equalsIgnoreCase(String.valueOf(c.get("status"))))
                .count();
        long pendingReview = allCourses.stream()
                .filter(c -> instructorId.equals(String.valueOf(c.get("createdBy"))))
                .filter(c -> "PENDING_REVIEW".equalsIgnoreCase(String.valueOf(c.get("status"))))
                .count();

        // Enrollment stats
        List<Map<String, Object>> enrollments = enrollmentServiceClient.getEnrollments(null, null, auth);
        long totalStudents = enrollments.stream()
                .filter(e -> String.valueOf(e.get("instructorId")).equals(instructorId))
                .map(e -> e.get("studentId"))
                .distinct()
                .count();

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("instructorId", instructorId);
        dashboard.put("createdCourses", createdCourses);
        dashboard.put("assignedCourses", totalAssigned);
        dashboard.put("totalManagedCourses", createdCourses + totalAssigned);
        dashboard.put("publishedCourses", publishedCourses);
        dashboard.put("pendingReview", pendingReview);
        dashboard.put("totalStudentsEnrolled", totalStudents);

        return ResponseEntity.ok(dashboard);
    }
}
