package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.repository.course.CourseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    @Autowired
    private CourseRepository courseRepository;

    @GetMapping("/courses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCourseStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCourses", courseRepository.count());
        stats.put("activeCourses", courseRepository.countByIsActive(true));
        stats.put("inactiveCourses", courseRepository.countByIsActive(false));
        stats.put("pendingReview", courseRepository.countByStatusIgnoreCase("pending"));
        stats.put("publishedCourses", courseRepository.countByStatusIgnoreCase("published"));
        return ResponseEntity.ok(stats);
    }
}
