package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.Course;
import com.cyberlearnix.shared.entity.CourseSuggestion;
import com.cyberlearnix.shared.repository.CourseRepository;
import com.cyberlearnix.shared.repository.CourseSuggestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    @Autowired
    private CourseSuggestionRepository suggestionRepository;

    @Autowired
    private CourseRepository courseRepository;

    @GetMapping
    public ResponseEntity<?> getSuggestions(@RequestParam(required = false) Long courseId,
            @RequestHeader(value = "X-User-Id", required = true) String userId,
            @RequestHeader(value = "X-User-Role", required = true) String userRole) {

        List<CourseSuggestion> suggestions;
        if ("teacher".equals(userRole) || "dual".equals(userRole)) {
            // Teachers only see suggestions for their courses
            List<Long> teacherCourseIds = courseRepository.findByCreatedBy(userId).stream()
                    .map(Course::getId)
                    .collect(Collectors.toList());
            if (courseId != null) {
                if (!teacherCourseIds.contains(courseId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                suggestions = suggestionRepository.findByCourseId(courseId);
            } else {
                suggestions = suggestionRepository.findByCourseIdIn(teacherCourseIds);
            }
        } else if ("admin".equals(userRole)) {
            // Admin sees all
            if (courseId != null) {
                suggestions = suggestionRepository.findByCourseId(courseId);
            } else {
                suggestions = suggestionRepository.findAll();
            }
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(Map.of("success", true, "suggestions", suggestions));
    }

    @PostMapping
    public ResponseEntity<?> createSuggestion(@RequestBody CourseSuggestion suggestion,
            @RequestHeader(value = "X-User-Id", required = true) String userId,
            @RequestHeader(value = "X-User-Role", required = true) String userRole) {
        if (!"admin".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins can suggest"));
        }
        suggestion.setAdminId(userId);
        suggestion.setCreatedAt(LocalDateTime.now());
        suggestion.setStatus("pending");
        return ResponseEntity.status(HttpStatus.CREATED).body(suggestionRepository.save(suggestion));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSuggestion(@PathVariable Long id, @RequestBody Map<String, String> statusMap,
            @RequestHeader(value = "X-User-Id", required = true) String userId,
            @RequestHeader(value = "X-User-Role", required = true) String userRole) {
        return suggestionRepository.findById(id).map(suggestion -> {
            Optional<Course> course = courseRepository.findById(suggestion.getCourseId());
            if (!"admin".equals(userRole) && (course.isEmpty() || !course.get().getCreatedBy().equals(userId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            suggestion.setStatus(statusMap.get("status"));
            return ResponseEntity.ok(suggestionRepository.save(suggestion));
        }).orElse(ResponseEntity.notFound().build());
    }
}
