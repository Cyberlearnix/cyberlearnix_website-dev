package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.Course;
import com.cyberlearnix.shared.entity.course.CourseTeacher;
import com.cyberlearnix.shared.repository.course.CourseRepository;
import com.cyberlearnix.shared.repository.course.CourseTeacherRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/teacher")
public class TeacherDashboardController {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseTeacherRepository courseTeacherRepository;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('TEACHER','DUAL','ADMIN')")
    public ResponseEntity<?> getTeacherDashboard(HttpServletRequest request) {
        String teacherId = (String) request.getAttribute("X-User-Id");
        if (teacherId == null || teacherId.isBlank()) {
            teacherId = request.getHeader("X-User-Id");
        }

        if (teacherId == null || teacherId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing authenticated teacher id"));
        }

        List<Course> createdCourses = courseRepository.findByCreatedBy(teacherId);
        List<CourseTeacher> assignedMappings = courseTeacherRepository.findByTeacherId(teacherId);

        Set<Long> managedCourseIds = new HashSet<>();
        for (Course course : createdCourses) {
            managedCourseIds.add(course.getId());
        }
        for (CourseTeacher mapping : assignedMappings) {
            managedCourseIds.add(mapping.getCourseId());
        }

        List<Course> managedCourses = courseRepository.findAllById(managedCourseIds);

        long activeCourses = managedCourses.stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .count();

        long pendingReview = managedCourses.stream()
                .filter(c -> c.getStatus() != null && "pending".equalsIgnoreCase(c.getStatus()))
                .count();

        long publishedCourses = managedCourses.stream()
                .filter(c -> c.getStatus() != null && "published".equalsIgnoreCase(c.getStatus()))
                .count();

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("teacherId", teacherId);
        dashboard.put("createdCourses", createdCourses.size());
        dashboard.put("assignedCourses", assignedMappings.size());
        dashboard.put("totalManagedCourses", managedCourseIds.size());
        dashboard.put("activeCourses", activeCourses);
        dashboard.put("pendingReview", pendingReview);
        dashboard.put("publishedCourses", publishedCourses);

        return ResponseEntity.ok(Map.of("success", true, "dashboard", dashboard));
    }
}
