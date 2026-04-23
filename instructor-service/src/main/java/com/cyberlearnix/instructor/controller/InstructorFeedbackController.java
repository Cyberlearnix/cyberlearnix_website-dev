package com.cyberlearnix.instructor.controller;

import com.cyberlearnix.instructor.client.EnrollmentServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/instructor/feedback")
@RequiredArgsConstructor
public class InstructorFeedbackController {

    private final EnrollmentServiceClient enrollmentServiceClient;

    /**
     * GET /api/instructor/feedback
     * Returns feedback/ratings received by the instructor across their courses.
     * Fetched from enrollment-service where student reviews are stored.
     *
     * Future scope: connect to a dedicated review/rating service.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getFeedback(
            @RequestParam(required = false) Long courseId,
            @RequestHeader("Authorization") String auth,
            Authentication authentication) {

        String instructorId = authentication.getName();

        // Fetch enrollments for the courses managed by the instructor
        List<Map<String, Object>> enrollments = enrollmentServiceClient.getEnrollments(null, courseId, auth);

        // Filter feedback entries that have a rating field (future: dedicated reviews endpoint)
        List<Map<String, Object>> feedbackList = enrollments.stream()
                .filter(e -> e.containsKey("rating") && e.get("rating") != null)
                .collect(Collectors.toList());

        double avgRating = feedbackList.stream()
                .map(e -> e.get("rating"))
                .filter(r -> r instanceof Number)
                .mapToDouble(r -> ((Number) r).doubleValue())
                .average()
                .orElse(0.0);

        return ResponseEntity.ok(Map.of(
                "instructorId", instructorId,
                "totalFeedback", feedbackList.size(),
                "averageRating", Math.round(avgRating * 10.0) / 10.0,
                "feedback", feedbackList
        ));
    }
}
