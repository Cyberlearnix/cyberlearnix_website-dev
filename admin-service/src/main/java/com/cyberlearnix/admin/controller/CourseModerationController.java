package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.client.CourseServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
public class CourseModerationController {

    private final CourseServiceClient courseServiceClient;

    @GetMapping
    public List<Map<String, Object>> getAllCourses(@RequestParam(required = false) String status,
                                                    @RequestHeader("Authorization") String auth) {
        return courseServiceClient.getAllCourses(status, auth);
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveCourse(@PathVariable Long id,
                                            @RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(
                courseServiceClient.updateCourseStatus(id, Map.of("status", "APPROVED"), auth)
        );
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectCourse(@PathVariable Long id,
                                           @RequestBody Map<String, String> reasonRequest,
                                           @RequestHeader("Authorization") String auth) {
        Map<String, String> body = Map.of(
                "status", "REJECTED",
                "reason", reasonRequest.getOrDefault("reason", "")
        );
        return ResponseEntity.ok(courseServiceClient.updateCourseStatus(id, body, auth));
    }

    @GetMapping("/content/{courseId}")
    public ResponseEntity<List<Map<String, Object>>> getCourseContent(@PathVariable Long courseId,
                                                                        @RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(courseServiceClient.getCourseContent(courseId, auth));
    }

    @PutMapping("/content/{id}/approve")
    public ResponseEntity<?> approveContent(@PathVariable Long id,
                                             @RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(
                courseServiceClient.updateContentStatus(id, Map.of("status", "APPROVED"), auth)
        );
    }

    @PutMapping("/content/{id}/reject")
    public ResponseEntity<?> rejectContent(@PathVariable Long id,
                                            @RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(
                courseServiceClient.updateContentStatus(id, Map.of("status", "REJECTED"), auth)
        );
    }
}
