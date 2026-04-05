package com.cyberlearnix.admin.controller;

import com.cyberlearnix.shared.entity.course.Course;
import com.cyberlearnix.shared.entity.course.ModuleContent;
import com.cyberlearnix.shared.repository.CourseRepository;
import com.cyberlearnix.shared.repository.ModuleContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
public class CourseModerationController {

    private final CourseRepository courseRepository;
    private final ModuleContentRepository moduleContentRepository;

    @GetMapping
    public List<Course> getAllCourses(@RequestParam(required = false) String status) {
        if (status != null) {
            return courseRepository.findAll().stream()
                    .filter(c -> c.getStatus().equalsIgnoreCase(status))
                    .toList();
        }
        return courseRepository.findAll();
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveCourse(@PathVariable Long id) {
        return courseRepository.findById(id)
                .map(course -> {
                    course.setStatus("APPROVED");
                    course.setUpdatedAt(LocalDateTime.now());
                    courseRepository.save(course);
                    return ResponseEntity.ok(Map.of("message", "Course approved successfully", "status", "APPROVED"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectCourse(@PathVariable Long id, @RequestBody Map<String, String> reasonRequest) {
        return courseRepository.findById(id)
                .map(course -> {
                    course.setStatus("REJECTED");
                    course.setUpdatedAt(LocalDateTime.now());
                    courseRepository.save(course);
                    // In a real app, send notification to instructor with reasonRequest.get("reason")
                    return ResponseEntity.ok(Map.of("message", "Course rejected successfully", "status", "REJECTED"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/content/{courseId}")
    public ResponseEntity<List<ModuleContent>> getCourseContent(@PathVariable Long courseId) {
        // This is a bit simplified, ideally find by courseId through modules
        List<ModuleContent> contents = moduleContentRepository.findAll().stream()
                .filter(mc -> mc.getModule().getCourse().getId().equals(courseId))
                .toList();
        return ResponseEntity.ok(contents);
    }

    @PutMapping("/content/{id}/approve")
    public ResponseEntity<?> approveContent(@PathVariable Long id) {
        return moduleContentRepository.findById(id)
                .map(content -> {
                    content.setStatus("APPROVED");
                    content.setUpdatedAt(LocalDateTime.now());
                    moduleContentRepository.save(content);
                    return ResponseEntity.ok(Map.of("message", "Content approved successfully", "status", "APPROVED"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/content/{id}/reject")
    public ResponseEntity<?> rejectContent(@PathVariable Long id) {
        return moduleContentRepository.findById(id)
                .map(content -> {
                    content.setStatus("REJECTED");
                    content.setUpdatedAt(LocalDateTime.now());
                    moduleContentRepository.save(content);
                    return ResponseEntity.ok(Map.of("message", "Content rejected successfully", "status", "REJECTED"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
