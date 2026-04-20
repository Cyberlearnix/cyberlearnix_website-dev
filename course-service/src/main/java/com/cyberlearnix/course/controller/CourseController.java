package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.Course;
import com.cyberlearnix.shared.entity.course.CourseModule;
import com.cyberlearnix.shared.entity.course.ModuleContent;
import com.cyberlearnix.shared.entity.course.CourseTeacher;
import com.cyberlearnix.shared.entity.course.CourseTeacherId;
import com.cyberlearnix.shared.entity.course.ContentPartner;
import com.cyberlearnix.shared.entity.course.CourseSuggestion;
import com.cyberlearnix.shared.entity.course.LabContent;
import com.cyberlearnix.shared.entity.course.LectureContent;
import com.cyberlearnix.shared.entity.course.QuizContent;
import com.cyberlearnix.shared.entity.course.QuizQuestion;
import com.cyberlearnix.shared.entity.course.QuestionOption;
import com.cyberlearnix.shared.entity.course.AssignmentContent;
import com.cyberlearnix.shared.entity.course.ContentReview;
import com.cyberlearnix.shared.entity.course.ContentUpdate;
import com.cyberlearnix.shared.entity.course.Banner;
import com.cyberlearnix.shared.entity.course.PromoBanner;
import com.cyberlearnix.shared.repository.course.*;
import com.cyberlearnix.course.client.EnrollmentServiceClient;
import com.cyberlearnix.course.client.UserServiceClient;
import com.cyberlearnix.course.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
// ... existing autowired fields ...
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseTeacherRepository courseTeacherRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private EnrollmentServiceClient enrollmentServiceClient;

    @Autowired
    private UserServiceClient userServiceClient;

    @GetMapping
    public ResponseEntity<?> getCourses(@RequestParam(required = false) Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        if (id != null) {
            return courseRepository.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        if (userId == null || userRole == null) {
            return ResponseEntity.ok(Map.of("success", true, "courses", courseRepository.findAll()));
        }

        List<Course> courses = new ArrayList<>();
        String normalizedRole = userRole != null ? userRole.toLowerCase() : "";
        if ("student".equals(normalizedRole)) {
            // Students can browse all courses, but might want to see their enrolled ones first
            // Standard browse: see all active/approved courses
            courses = courseRepository.findAll().stream()
                    .filter(c -> c.getActive() != null && c.getActive())
                    .collect(Collectors.toList());
        } else if ("admin".equals(normalizedRole) || "administrator".equals(normalizedRole)) {
            // Admin sees all
            courses = courseRepository.findAll();
        } else if ("teacher".equals(normalizedRole) || "dual".equals(normalizedRole)) {
            // Teachers see their own courses OR assigned courses
            List<Course> teacherCourses = courseRepository.findByCreatedBy(userId);
            List<Long> assignedCourseIds = courseTeacherRepository.findByTeacherId(userId).stream()
                    .map(CourseTeacher::getCourseId)
                    .collect(Collectors.toList());
            List<Course> assignedCourses = courseRepository.findAllById(assignedCourseIds);

            courses.addAll(teacherCourses);
            courses.addAll(assignedCourses);

            if ("dual".equals(normalizedRole)) {
                // If dual, also see enrolled student courses (via enrollment-service)
                try {
                    List<Long> enrolledCourseIds = enrollmentServiceClient.getEnrollments(userId, null).stream()
                            .map(e -> ((Number) e.get("courseId")).longValue())
                            .collect(Collectors.toList());
                    courses.addAll(courseRepository.findAllById(enrolledCourseIds));
                } catch (Exception ignored) {
                }
            }
        }

        // Remove duplicates if any
        courses = courses.stream().distinct().collect(Collectors.toList());

        // Enrich with moduleCount using a single batch query
        List<Long> courseIds = courses.stream().map(Course::getId).collect(Collectors.toList());
        Map<Long, Long> moduleCountMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            moduleRepository.countByCourseIdIn(courseIds)
                    .forEach(row -> moduleCountMap.put((Long) row[0], (Long) row[1]));
        }
        List<Map<String, Object>> enrichedCourses = courses.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("title", c.getTitle());
            map.put("description", c.getDescription());
            map.put("category", c.getCategory());
            map.put("difficultyLevel", c.getDifficultyLevel());
            map.put("duration", c.getDuration());
            map.put("contentUrl", c.getContentUrl());
            map.put("thumbnailUrl", c.getThumbnailUrl());
            map.put("basePrice", c.getBasePrice());
            map.put("gstPercent", c.getGstPercent());
            map.put("finalPrice", c.getFinalPrice());
            map.put("isActive", c.getActive());
            map.put("createdBy", c.getCreatedBy());
            map.put("createdAt", c.getCreatedAt());
            map.put("updatedAt", c.getUpdatedAt());
            map.put("status", c.getStatus());
            map.put("moduleCount", moduleCountMap.getOrDefault(c.getId(), 0L));
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("success", true, "courses", enrichedCourses));
    }

    @PostMapping
    public ResponseEntity<?> createCourse(@RequestBody CourseCreateDTO courseDTO,
            @RequestHeader(value = "X-User-Id", required = true) String userId,
            @RequestHeader(value = "X-User-Role", required = true) String userRole) {

        if ("student".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Students cannot create courses"));
        }

        if ("teacher".equals(userRole) || "dual".equals(userRole)) {
            try {
                Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
                if (perm == null || !Boolean.TRUE.equals(perm.get("canCreateCourses"))) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "No permission to create courses"));
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No permission to create courses"));
            }
        }

        Course course = new Course();
        course.setTitle(courseDTO.getTitle());
        course.setDescription(courseDTO.getDescription());
        course.setContentUrl(courseDTO.getContentUrl());
        course.setThumbnailUrl(courseDTO.getThumbnailUrl());
        course.setBasePrice(courseDTO.getBasePrice());
        course.setGstPercent(courseDTO.getGstPercent());
        course.setFinalPrice(courseDTO.getFinalPrice());
        course.setCategory(courseDTO.getCategory());
        course.setDifficultyLevel(courseDTO.getDifficultyLevel());
        course.setDuration(courseDTO.getDuration());
        course.setActive(courseDTO.getIsActive() != null ? courseDTO.getIsActive() : true);
        
        course.setCreatedBy(userId);
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());

        Course saved = courseRepository.save(course);
        
        // AUTO-ASSIGN creator as teacher so they can edit it
        CourseTeacher ct = new CourseTeacher();
        ct.setCourseId(saved.getId());
        ct.setTeacherId(userId);
        courseTeacherRepository.save(ct);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "course", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable Long id, @RequestBody CourseUpdateDTO courseDTO,
            @RequestHeader("X-User-Id") String userId, @RequestHeader("X-User-Role") String userRole) {
        return courseRepository.findById(id).map(course -> {
            boolean isAdmin = "admin".equals(userRole);
            boolean isAssignedTeacher = courseTeacherRepository.existsByCourseIdAndTeacherId(id, userId);

            if (!isAdmin && !isAssignedTeacher) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No permission to update this course"));
            }

            if (courseDTO.getTitle() != null)
                course.setTitle(courseDTO.getTitle());
            if (courseDTO.getDescription() != null)
                course.setDescription(courseDTO.getDescription());
            if (courseDTO.getContentUrl() != null)
                course.setContentUrl(courseDTO.getContentUrl());
            if (courseDTO.getThumbnailUrl() != null)
                course.setThumbnailUrl(courseDTO.getThumbnailUrl());
            if (courseDTO.getBasePrice() != null)
                course.setBasePrice(courseDTO.getBasePrice());
            if (courseDTO.getGstPercent() != null)
                course.setGstPercent(courseDTO.getGstPercent());
            if (courseDTO.getFinalPrice() != null)
                course.setFinalPrice(courseDTO.getFinalPrice());
            if (courseDTO.getCategory() != null)
                course.setCategory(courseDTO.getCategory());
            if (courseDTO.getDifficultyLevel() != null)
                course.setDifficultyLevel(courseDTO.getDifficultyLevel());
            if (courseDTO.getDuration() != null)
                course.setDuration(courseDTO.getDuration());
            if (courseDTO.getIsActive() != null)
                course.setActive(courseDTO.getIsActive());

            course.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(Map.of("success", true, "course", courseRepository.save(course)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        if (userId == null || userRole == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        boolean isAdmin = "admin".equals(userRole);
        boolean isAssignedTeacher = courseTeacherRepository.existsByCourseIdAndTeacherId(id, userId);
        if (!isAdmin && !isAssignedTeacher) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No permission to delete this course"));
        }
        if (courseRepository.existsById(id)) {
            courseRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.notFound().build();
    }

    // Student View: Full Curriculum (Modules & Content Titles)
    @GetMapping("/{id}/curriculum")
    public ResponseEntity<?> getCourseCurriculum(@PathVariable Long id) {
        return courseRepository.findById(id).map(course -> {
            List<CourseModule> modules = moduleRepository.findByCourseIdOrderByOrderIndex(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "courseTitle", course.getTitle(),
                    "modules", modules));
        }).orElse(ResponseEntity.notFound().build());
    }
}
