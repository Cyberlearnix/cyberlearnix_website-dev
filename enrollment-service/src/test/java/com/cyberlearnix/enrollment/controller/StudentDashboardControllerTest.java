package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.enrollment.client.CourseServiceClient;
import com.cyberlearnix.shared.entity.enrollment.Enrollment;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link StudentDashboardController}.
 *
 * Uses standalone MockMvc — Spring Security filters are bypassed so tests
 * focus on the controller's own logic (stats computation, Feign graceful fallback).
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StudentDashboardControllerTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseServiceClient courseServiceClient;

    @InjectMocks
    private StudentDashboardController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ────────────────────────────────────────────────────────────────
    // Helper
    // ────────────────────────────────────────────────────────────────

    private Enrollment enrollment(Long id, Long courseId, int progress) {
        Enrollment e = new Enrollment();
        e.setId(id);
        e.setCourseId(courseId);
        e.setStudentId("student-1");
        e.setProgress(progress);
        return e;
    }

    private Map<String, Object> fakeCourseInfo(Long courseId, String title) {
        return Map.of(
                "id", courseId,
                "title", title,
                "thumbnailUrl", "https://example.com/thumb.jpg",
                "description", "A great course",
                "category", "Cybersecurity",
                "difficultyLevel", "Beginner",
                "duration", "10h"
        );
    }

    // ────────────────────────────────────────────────────────────────
    // /api/student/dashboard
    // ────────────────────────────────────────────────────────────────

    @Test
    void dashboard_noEnrollments_returnsEmptyStatsAndList() throws Exception {
        when(enrollmentRepository.findByStudentId("student-1"))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/student/dashboard")
                        .header("X-User-Id", "student-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.totalCourses", is(0)))
                .andExpect(jsonPath("$.stats.completedCourses", is(0)))
                .andExpect(jsonPath("$.stats.inProgressCourses", is(0)))
                .andExpect(jsonPath("$.stats.overallProgress", is(0.0)))
                .andExpect(jsonPath("$.enrollments", hasSize(0)));
    }

    @Test
    void dashboard_enrichedWithCourseInfo_returnsCorrectStats() throws Exception {
        Enrollment e1 = enrollment(1L, 10L, 100);  // completed
        Enrollment e2 = enrollment(2L, 11L, 50);   // in-progress
        Enrollment e3 = enrollment(3L, 12L, 0);    // not started

        when(enrollmentRepository.findByStudentId("student-1"))
                .thenReturn(List.of(e1, e2, e3));
        when(courseServiceClient.getCourseInfo(10L)).thenReturn(fakeCourseInfo(10L, "Network Hacking"));
        when(courseServiceClient.getCourseInfo(11L)).thenReturn(fakeCourseInfo(11L, "Web App Pentesting"));
        when(courseServiceClient.getCourseInfo(12L)).thenReturn(fakeCourseInfo(12L, "Malware Analysis"));

        mockMvc.perform(get("/api/student/dashboard")
                        .header("X-User-Id", "student-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.totalCourses", is(3)))
                .andExpect(jsonPath("$.stats.completedCourses", is(1)))
                .andExpect(jsonPath("$.stats.inProgressCourses", is(1)))
                .andExpect(jsonPath("$.enrollments", hasSize(3)))
                // First enrollment should be COMPLETED
                .andExpect(jsonPath("$.enrollments[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$.enrollments[0].courseTitle", is("Network Hacking")))
                // Second enrollment should be IN_PROGRESS
                .andExpect(jsonPath("$.enrollments[1].status", is("IN_PROGRESS")))
                // Third enrollment should be NOT_STARTED
                .andExpect(jsonPath("$.enrollments[2].status", is("NOT_STARTED")));
    }

    @Test
    void dashboard_overallProgressCalculatedCorrectly() throws Exception {
        Enrollment e1 = enrollment(1L, 10L, 60);
        Enrollment e2 = enrollment(2L, 11L, 40);

        when(enrollmentRepository.findByStudentId("student-1"))
                .thenReturn(List.of(e1, e2));
        when(courseServiceClient.getCourseInfo(anyLong()))
                .thenReturn(fakeCourseInfo(10L, "Course"));

        mockMvc.perform(get("/api/student/dashboard")
                        .header("X-User-Id", "student-1"))
                .andExpect(status().isOk())
                // (60 + 40) / 2 = 50.0
                .andExpect(jsonPath("$.stats.overallProgress", is(50.0)));
    }

    @Test
    void dashboard_gracefullyHandlesFeignException() throws Exception {
        Enrollment e1 = enrollment(1L, 10L, 25);

        when(enrollmentRepository.findByStudentId("student-1"))
                .thenReturn(List.of(e1));
        when(courseServiceClient.getCourseInfo(10L))
                .thenThrow(new RuntimeException("course-service down"));

        // Should still return 200 — courseTitle and category will be null
        mockMvc.perform(get("/api/student/dashboard")
                        .header("X-User-Id", "student-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.totalCourses", is(1)))
                .andExpect(jsonPath("$.enrollments[0].courseId", is(10)))
                .andExpect(jsonPath("$.enrollments[0].courseTitle").doesNotExist());
    }

    // ────────────────────────────────────────────────────────────────
    // /api/student/enrollments (alias endpoint — same logic)
    // ────────────────────────────────────────────────────────────────

    @Test
    void enrollments_endpoint_returnsSameDataAsDashboard() throws Exception {
        Enrollment e1 = enrollment(1L, 10L, 100);

        when(enrollmentRepository.findByStudentId("student-1"))
                .thenReturn(List.of(e1));
        when(courseServiceClient.getCourseInfo(10L))
                .thenReturn(fakeCourseInfo(10L, "Alias Test"));

        mockMvc.perform(get("/api/student/enrollments")
                        .header("X-User-Id", "student-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.totalCourses", is(1)))
                .andExpect(jsonPath("$.enrollments[0].courseTitle", is("Alias Test")));
    }
}
