package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.*;
import com.cyberlearnix.shared.repository.*;
import com.cyberlearnix.course.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private UserProfileRepository userProfileRepository;

    @Autowired
    private CourseTeacherRepository courseTeacherRepository;

    @Autowired
    private TeacherPermissionRepository teacherPermissionRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

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
        if ("student".equals(userRole)) {
            // Students can browse all courses, but might want to see their enrolled ones first
            // Standard browse: see all active/approved courses
            courses = courseRepository.findAll().stream()
                    .filter(c -> c.getActive() != null && c.getActive())
                    .collect(Collectors.toList());
        } else if ("admin".equals(userRole)) {
            // Admin sees all
            courses = courseRepository.findAll();
        } else if ("teacher".equals(userRole) || "dual".equals(userRole)) {
            // Teachers see their own courses OR assigned courses
            List<Course> teacherCourses = courseRepository.findByCreatedBy(userId);
            List<Long> assignedCourseIds = courseTeacherRepository.findByTeacherId(userId).stream()
                    .map(CourseTeacher::getCourseId)
                    .collect(Collectors.toList());
            List<Course> assignedCourses = courseRepository.findAllById(assignedCourseIds);

            courses.addAll(teacherCourses);
            courses.addAll(assignedCourses);

            if ("dual".equals(userRole)) {
                // If dual, also see enrolled student courses
                List<Course> enrolledCourses = enrollmentRepository.findByStudentId(userId).stream()
                        .map(e -> e.getCourse())
                        .collect(Collectors.toList());
                courses.addAll(enrolledCourses);
            }
        }

        // Remove duplicates if any
        courses = courses.stream().distinct().collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("success", true, "courses", courses));
    }

    @PostMapping
    public ResponseEntity<?> createCourse(@RequestBody CourseCreateDTO courseDTO,
            @RequestHeader(value = "X-User-Id", required = true) String userId,
            @RequestHeader(value = "X-User-Role", required = true) String userRole) {

        if ("student".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Students cannot create courses"));
        }

        if ("teacher".equals(userRole) || "dual".equals(userRole)) {
            Optional<TeacherPermission> p = teacherPermissionRepository.findById(userId);
            if (p.isEmpty() || !p.get().getCanCreateCourses()) {
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
