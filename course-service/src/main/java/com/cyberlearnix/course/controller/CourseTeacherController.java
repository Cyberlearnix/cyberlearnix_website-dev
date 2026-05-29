package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.CourseTeacher;
import com.cyberlearnix.shared.repository.course.CourseRepository;
import com.cyberlearnix.shared.repository.course.CourseTeacherRepository;
import com.cyberlearnix.course.client.UserServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles GET/POST/DELETE /api/courses/teachers
 * Used by user-management.js for teacher course assignments
 * and by user-management.js#loadAndRenderAssignedCourses (teacher mode)
 */
@RestController
@RequestMapping("/api/courses/teachers")
public class CourseTeacherController {

    @Autowired
    private CourseTeacherRepository courseTeacherRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserServiceClient userServiceClient;

    /**
     * GET /api/courses/teachers/exists?teacherId={id}&courseId={id}
     * Check if a teacher is assigned to a course. Used by enrollment-service Feign client.
     */
    @GetMapping("/exists")
    public ResponseEntity<Boolean> teacherExistsForCourse(
            @RequestParam String teacherId,
            @RequestParam Long courseId) {
        return ResponseEntity.ok(courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, teacherId));
    }

    /**
     * GET /api/courses/teachers?teacherId={id}
     * Get all courses assigned to a teacher. Used by user-management.js.
     */
    @GetMapping
    public ResponseEntity<?> getAssignedCourses(@RequestParam(required = false) String teacherId,
            @RequestParam(required = false) Long courseId) {
        if (teacherId != null) {
            List<Map<String, Object>> result = courseTeacherRepository.findByTeacherId(teacherId).stream()
                    .map(ct -> {
                        Map<String, Object> entry = new java.util.HashMap<>();
                        entry.put("id", ct.getCourseId() + "|" + ct.getTeacherId()); // composite key for removal
                        entry.put("courseId", ct.getCourseId());
                        entry.put("teacherId", ct.getTeacherId());
                        courseRepository.findById(ct.getCourseId())
                                .ifPresent(c -> entry.put("course", Map.of("id", c.getId(), "title", c.getTitle())));
                        return entry;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        }
        if (courseId != null) {
            List<Map<String, Object>> result = courseTeacherRepository.findByCourseId(courseId).stream()
                    .map(ct -> {
                        Map<String, Object> entry = new java.util.HashMap<>();
                        entry.put("courseId", ct.getCourseId());
                        entry.put("teacherId", ct.getTeacherId());
                        try {
                            Map<String, Object> profile = userServiceClient.getUserProfile(ct.getTeacherId());
                            if (profile != null) {
                                entry.put("teacher", Map.of(
                                    "id", ct.getTeacherId(),
                                    "fullName", profile.getOrDefault("fullName", "")));
                            }
                        } catch (Exception ignored) {}
                        return entry;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(courseTeacherRepository.findAll());
    }

    /**
     * POST /api/courses/teachers
     * Assign a teacher to a course. Body: { courseId, teacherId }
     * Used by user-management.js#assignCourseToTeacher
     */
    @PostMapping
    public ResponseEntity<?> assignTeacher(@RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only administrators can assign courses to teachers"));
        }

        Long courseId = Long.valueOf(String.valueOf(payload.get("courseId")));
        String teacherId = (String) payload.get("teacherId");

        if (courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, teacherId)) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Teacher already assigned"));
        }

        CourseTeacher ct = new CourseTeacher();
        ct.setCourseId(courseId);
        ct.setTeacherId(teacherId);
        courseTeacherRepository.save(ct);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * DELETE /api/courses/teachers
     * Remove a teacher from a course. Body: { courseId, teacherId }
     * Used by user-management.js#removeCourseAssignment (teacher mode)
     */
    @DeleteMapping
    @Transactional
    public ResponseEntity<?> removeTeacher(@RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only administrators can remove course assignments"));
        }

        Long courseId = Long.valueOf(String.valueOf(payload.get("courseId")));
        String teacherId = (String) payload.get("teacherId");
        courseTeacherRepository.deleteByCourseIdAndTeacherId(courseId, teacherId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
