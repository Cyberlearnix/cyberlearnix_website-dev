package com.cyberlearnix.instructor.controller;

import com.cyberlearnix.instructor.client.EnrollmentServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/instructor/students")
@RequiredArgsConstructor
public class InstructorStudentController {

    private final EnrollmentServiceClient enrollmentServiceClient;

    /**
     * GET /api/instructor/students
     * Returns a paginated list of all students enrolled in the instructor's courses.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllStudents(
            @RequestParam(required = false) Long courseId,
            @RequestHeader("Authorization") String auth) {

        return ResponseEntity.ok(enrollmentServiceClient.getEnrollments(null, courseId, auth));
    }

    /**
     * GET /api/instructor/students/course/{courseId}
     * Returns students enrolled in a specific course of the instructor.
     */
    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getStudentsByCourse(
            @PathVariable Long courseId,
            @RequestHeader("Authorization") String auth) {

        return ResponseEntity.ok(enrollmentServiceClient.getStudentsByCourse(courseId, auth));
    }

    /**
     * GET /api/instructor/students/{studentId}/progress
     * Returns progress details of a specific student, optionally filtered by course.
     */
    @GetMapping("/{studentId}/progress")
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getStudentProgress(
            @PathVariable String studentId,
            @RequestParam(required = false) Long courseId,
            @RequestHeader("Authorization") String auth) {

        Map<String, Object> progress = enrollmentServiceClient.getStudentProgress(studentId, courseId, auth);
        return ResponseEntity.ok(progress);
    }
}
