package com.cyberlearnix.instructor.controller;

import com.cyberlearnix.instructor.client.CourseServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/instructor/courses")
@RequiredArgsConstructor
public class InstructorCourseController {

    private final CourseServiceClient courseServiceClient;

    /**
     * GET /api/instructor/courses
     * Returns all courses assigned to or created by the authenticated instructor.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getMyCourses(
            @RequestParam(required = false) String status,
            @RequestHeader("Authorization") String auth,
            Authentication authentication) {

        String instructorId = authentication.getName();

        Map<String, Object> coursesResp = courseServiceClient.getAllCourses(status, auth);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allCourses = coursesResp.containsKey("courses")
                ? (List<Map<String, Object>>) coursesResp.get("courses")
                : java.util.Collections.emptyList();
        List<Map<String, Object>> myCourses = allCourses.stream()
                .filter(c -> instructorId.equals(String.valueOf(c.get("createdBy"))))
                .collect(Collectors.toList());

        return ResponseEntity.ok(myCourses);
    }

    /**
     * GET /api/instructor/courses/{courseId}
     * Returns details of a specific course if the instructor is assigned.
     */
    @GetMapping("/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getCourseDetail(
            @PathVariable Long courseId,
            @RequestHeader("Authorization") String auth) {

        Map<String, Object> course = courseServiceClient.getCourseById(courseId, auth);
        return ResponseEntity.ok(course);
    }

    /**
     * GET /api/instructor/courses/{courseId}/content
     * Returns content/curriculum of a course the instructor manages.
     */
    @GetMapping("/{courseId}/content")
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getCourseContent(
            @PathVariable Long courseId,
            @RequestHeader("Authorization") String auth) {

        List<Map<String, Object>> content = courseServiceClient.getCourseContent(courseId, auth);
        return ResponseEntity.ok(content);
    }

    /**
     * POST /api/instructor/courses/{courseId}/supplemental-content
     * Allows an instructor to add supplemental/extra content to an assigned course.
     */
    @PostMapping("/{courseId}/supplemental-content")
    @PreAuthorize("hasAnyRole('TEACHER', 'DUAL')")
    public ResponseEntity<Map<String, Object>> addSupplementalContent(
            @PathVariable Long courseId,
            @RequestBody Map<String, Object> contentRequest,
            @RequestHeader("Authorization") String auth) {

        contentRequest.put("courseId", courseId);
        contentRequest.put("contentType", "SUPPLEMENTAL");
        Map<String, Object> result = courseServiceClient.addCourseContent(contentRequest, auth);
        return ResponseEntity.ok(result);
    }
}
