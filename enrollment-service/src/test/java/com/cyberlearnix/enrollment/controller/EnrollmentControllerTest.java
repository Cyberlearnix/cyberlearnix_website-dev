package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.enrollment.client.CourseServiceClient;
import com.cyberlearnix.enrollment.exception.GlobalExceptionHandler;
import com.cyberlearnix.enrollment.service.EnrollmentService;
import com.cyberlearnix.shared.entity.enrollment.Enrollment;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link EnrollmentController} covering SEC-001, SEC-002, and BUG-003.
 *
 * Uses standalone MockMvc so Spring Security filters are bypassed; the tests focus
 * exclusively on the controller's own header-based access control logic.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class EnrollmentControllerTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EnrollmentService enrollmentService;
    @Mock private CourseServiceClient courseServiceClient;

    @InjectMocks
    private EnrollmentController controller;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // ── SEC-001: PATCH /api/enrollments/progress ──────────────────────────────

    // Guarantees: a student who is not the subject of the update receives 403, not 200
    @Test
    void patchProgress_returns403_whenCallerIsADifferentStudent() throws Exception {
        Map<String, Object> body = Map.of("studentId", "user-A", "courseId", 1, "progress", 50);

        mockMvc.perform(patch("/api/enrollments/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-User-Id", "user-B")
                        .header("X-User-Role", "student"))
                .andExpect(status().isForbidden());
    }

    // Guarantees: a student can update their own progress when callerId matches studentId
    @Test
    void patchProgress_returns200_whenCallerIsSelf() throws Exception {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId("user-A");
        enrollment.setCourseId(1L);
        enrollment.setProgress(0);
        when(enrollmentRepository.findByStudentIdAndCourseId("user-A", 1L))
                .thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any())).thenReturn(enrollment);

        Map<String, Object> body = Map.of("studentId", "user-A", "courseId", 1, "progress", 50);

        mockMvc.perform(patch("/api/enrollments/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-User-Id", "user-A")
                        .header("X-User-Role", "student"))
                .andExpect(status().isOk());
    }

    // Guarantees: admin role can update any student's progress regardless of caller identity
    @Test
    void patchProgress_returns200_whenCallerIsAdmin() throws Exception {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId("user-A");
        enrollment.setCourseId(1L);
        when(enrollmentRepository.findByStudentIdAndCourseId("user-A", 1L))
                .thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any())).thenReturn(enrollment);

        Map<String, Object> body = Map.of("studentId", "user-A", "courseId", 1, "progress", 80);

        mockMvc.perform(patch("/api/enrollments/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk());
    }

    // Guarantees: teacher role can update any student's progress
    @Test
    void patchProgress_returns200_whenCallerIsTeacher() throws Exception {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId("user-A");
        enrollment.setCourseId(1L);
        when(enrollmentRepository.findByStudentIdAndCourseId("user-A", 1L))
                .thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any())).thenReturn(enrollment);

        Map<String, Object> body = Map.of("studentId", "user-A", "courseId", 1, "progress", 70);

        mockMvc.perform(patch("/api/enrollments/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-User-Id", "teacher-1")
                        .header("X-User-Role", "teacher"))
                .andExpect(status().isOk());
    }

    // Guarantees: absent X-User headers produce 403, not a silent pass-through or NullPointerException
    @Test
    void patchProgress_returns403_whenHeadersAbsent() throws Exception {
        Map<String, Object> body = Map.of("studentId", "user-A", "courseId", 1, "progress", 50);

        mockMvc.perform(patch("/api/enrollments/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── SEC-002: POST /api/enrollments role guard ─────────────────────────────

    // Guarantees: student role cannot bypass the admin-only direct enrollment endpoint
    @Test
    void createEnrollment_returns403_whenRoleIsStudent() throws Exception {
        Map<String, Object> body = Map.of("studentId", "user-1", "courseId", 10);

        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-User-Role", "student"))
                .andExpect(status().isForbidden());
    }

    // Guarantees: missing X-User-Role header is treated as non-admin and rejected with 403
    @Test
    void createEnrollment_returns403_whenRoleHeaderAbsent() throws Exception {
        Map<String, Object> body = Map.of("studentId", "user-1", "courseId", 10);

        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // Guarantees: teacher role is not permitted to create direct enrollments
    @Test
    void createEnrollment_returns403_whenRoleIsTeacher() throws Exception {
        Map<String, Object> body = Map.of("studentId", "user-1", "courseId", 10);

        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-User-Role", "teacher"))
                .andExpect(status().isForbidden());
    }

    // ── BUG-003: POST /api/enrollments duplicate guard ────────────────────────

    // Guarantees: 409 Conflict is returned when the student is already enrolled in the course
    @Test
    void createEnrollment_returns409_whenDuplicateExists() throws Exception {
        Enrollment existing = new Enrollment();
        existing.setStudentId("user-1");
        existing.setCourseId(10L);
        when(enrollmentRepository.findByStudentIdAndCourseId("user-1", 10L))
                .thenReturn(Optional.of(existing));

        Map<String, Object> body = Map.of("studentId", "user-1", "courseId", 10);

        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-User-Role", "admin"))
                .andExpect(status().isConflict());
    }

    // Guarantees: a unique new enrollment is created and returns 201 Created
    @Test
    void createEnrollment_returns201_whenNoExistingEnrollment() throws Exception {
        when(enrollmentRepository.findByStudentIdAndCourseId("user-2", 10L))
                .thenReturn(Optional.empty());
        Enrollment saved = new Enrollment();
        saved.setStudentId("user-2");
        saved.setCourseId(10L);
        when(enrollmentRepository.save(any())).thenReturn(saved);

        Map<String, Object> body = Map.of("studentId", "user-2", "courseId", 10);

        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-User-Role", "admin"))
                .andExpect(status().isCreated());
    }
}
