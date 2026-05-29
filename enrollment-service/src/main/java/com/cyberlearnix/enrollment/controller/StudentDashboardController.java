package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.shared.entity.enrollment.Enrollment;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentRepository;
import com.cyberlearnix.enrollment.client.CourseServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentDashboardController {

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseServiceClient courseServiceClient;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @RequestHeader("X-User-Id") String studentId) {
        return ResponseEntity.ok(buildDashboard(studentId));
    }

    @GetMapping("/enrollments")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ResponseEntity<Map<String, Object>> getEnrollments(
            @RequestHeader("X-User-Id") String studentId) {
        return ResponseEntity.ok(buildDashboard(studentId));
    }

    private Map<String, Object> buildDashboard(String studentId) {
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(studentId);

        int totalCourses = enrollments.size();
        int completedCourses = 0;
        int inProgressCourses = 0;
        double progressSum = 0.0;

        List<Map<String, Object>> enrollmentList = new ArrayList<>();

        for (Enrollment e : enrollments) {
            int progress = e.getProgress() != null ? e.getProgress() : 0;
            progressSum += progress;
            if (progress == 100) completedCourses++;
            else if (progress > 0) inProgressCourses++;

            String status;
            if (progress == 100) status = "COMPLETED";
            else if (progress > 0) status = "IN_PROGRESS";
            else status = "NOT_STARTED";

            Map<String, Object> courseInfo = null;
            try {
                courseInfo = courseServiceClient.getCourseInfo(e.getCourseId());
            } catch (Exception ignored) {
                // course-service unavailable — continue with nulls
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("enrollmentId", e.getId());
            item.put("courseId", e.getCourseId());
            item.put("courseTitle", courseInfo != null ? courseInfo.get("title") : null);
            item.put("courseThumbnail", courseInfo != null ? courseInfo.get("thumbnailUrl") : null);
            item.put("courseDescription", courseInfo != null ? courseInfo.get("description") : null);
            item.put("category", courseInfo != null ? courseInfo.get("category") : null);
            item.put("difficultyLevel", courseInfo != null ? courseInfo.get("difficultyLevel") : null);
            item.put("duration", courseInfo != null ? courseInfo.get("duration") : null);
            item.put("progress", progress);
            item.put("enrolledAt", e.getEnrolledAt());
            item.put("completedAt", e.getCompletedAt());
            item.put("status", status);
            enrollmentList.add(item);
        }

        double overallProgress = totalCourses > 0 ? progressSum / totalCourses : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCourses", totalCourses);
        stats.put("completedCourses", completedCourses);
        stats.put("inProgressCourses", inProgressCourses);
        stats.put("overallProgress", Math.round(overallProgress * 10.0) / 10.0);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("stats", stats);
        response.put("enrollments", enrollmentList);
        return response;
    }
}
