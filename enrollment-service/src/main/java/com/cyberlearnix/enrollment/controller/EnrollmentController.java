package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.shared.entity.enrollment.Enrollment;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentRepository;
import com.cyberlearnix.enrollment.client.CourseServiceClient;
import com.cyberlearnix.enrollment.service.EnrollmentService;
import com.cyberlearnix.enrollment.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private static final String ROLE_ADMIN = "admin";

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private EnrollmentService enrollmentService;

    @GetMapping("/config")
    public ResponseEntity<?> getConfig(@RequestParam String formId, @RequestParam(required = false) String token) {
        if (token != null) {
            return enrollmentService.getConfigByToken(formId, token)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return enrollmentService.getConfig(formId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Autowired
    private CourseServiceClient courseServiceClient;

    @GetMapping
    public ResponseEntity<?> getEnrollments(@RequestParam(required = false) String studentId,
            @RequestParam(required = false) Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        String targetStudentId = (studentId != null) ? studentId : userId;

        if (targetStudentId != null && courseId != null) {
            return enrollmentRepository.findByStudentIdAndCourseId(targetStudentId, courseId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } else if (targetStudentId != null && "student".equals(userRole)) {
            // Student sees only their own enrollments
            return ResponseEntity
                    .ok(Map.of("success", true, "enrollments", enrollmentRepository.findByStudentId(targetStudentId)));
        }

        if (ROLE_ADMIN.equals(userRole)) {
            return ResponseEntity.ok(Map.of("success", true, "enrollments", enrollmentRepository.findAll()));
        }

        if (("teacher".equals(userRole) || "dual".equals(userRole)) && courseId != null) {
            // Check if teacher is assigned to this course
            boolean isAssigned = false;
            try {
                isAssigned = courseServiceClient.teacherExistsForCourse(userId, courseId);
            } catch (Exception ignored) {}
            if (isAssigned) {
                return ResponseEntity
                        .ok(Map.of("success", true, "enrollments", enrollmentRepository.findByCourseId(courseId)));
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not assigned as teacher to this course"));
            }
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PatchMapping("/progress")
    public ResponseEntity<?> updateProgressByStudentAndCourse(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestHeader(value = "X-User-Role", required = false) String callerRole) {
        String studentId = (String) body.get("studentId");
        Long courseId = body.get("courseId") instanceof Number
                ? ((Number) body.get("courseId")).longValue()
                : Long.valueOf(String.valueOf(body.get("courseId")));
        Integer progress = body.get("progress") instanceof Number
                ? ((Number) body.get("progress")).intValue()
                : null;
        String completedAt = (String) body.get("completedAt");

        // SEC-001: Only allow admin, teacher/dual, or the student themselves
        boolean isAdmin = ROLE_ADMIN.equals(callerRole);
        boolean isTeacher = "teacher".equals(callerRole) || "dual".equals(callerRole);
        boolean isSelf = studentId != null && studentId.equals(callerId);
        if (!isAdmin && !isTeacher && !isSelf) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied: you can only update your own progress"));
        }

        return enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId).map(enrollment -> {
            if (progress != null) enrollment.setProgress(progress);
            if (completedAt != null) enrollment.setCompletedAt(LocalDateTime.parse(completedAt));
            else if (progress != null && progress >= 100) enrollment.setCompletedAt(LocalDateTime.now());
            return (ResponseEntity<?>) ResponseEntity.ok(Map.of("success", true, "enrollment", enrollmentRepository.save(enrollment)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createEnrollment(@RequestBody EnrollmentRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        // SEC-002: Only admins may create direct enrollments (normal flow goes through payment/verification)
        if (!ROLE_ADMIN.equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only admins can create direct enrollments"));
        }
        // BUG-003: Prevent duplicate enrollments
        if (enrollmentRepository.findByStudentIdAndCourseId(request.getStudentId(), request.getCourseId()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Student is already enrolled in this course"));
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(request.getStudentId());
        enrollment.setCourseId(request.getCourseId());
        enrollment.setEnrolledAt(LocalDateTime.now());

        Enrollment saved = enrollmentRepository.save(enrollment);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "enrollment", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProgress(@PathVariable Long id, @RequestBody ProgressUpdateRequest progressRequest) {
        return enrollmentRepository.findById(id).map(enrollment -> {
            if (progressRequest.getProgress() != null)
                enrollment.setProgress(progressRequest.getProgress());
            if (progressRequest.getCompletedAt() != null)
                enrollment.setCompletedAt(LocalDateTime.parse(progressRequest.getCompletedAt()));
            return ResponseEntity.ok(Map.of("success", true, "enrollment", enrollmentRepository.save(enrollment)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<?> verifyPayment(@RequestBody PaymentVerificationRequest verificationRequest,
            @RequestHeader(value = "Authorization") String token,
            @RequestHeader(value = "X-User-Id", required = true) String adminId,
            @RequestHeader(value = "X-User-Role", required = true) String userRole) {

        if (!ROLE_ADMIN.equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long enrollmentId = verificationRequest.getEnrollmentId();
        String action = verificationRequest.getAction();
        String rejectionReason = verificationRequest.getRejectionReason();

        return ResponseEntity.ok(enrollmentService.verifyPayment(enrollmentId, action, rejectionReason, adminId, token));
    }

    @PostMapping("/bulk-assign")
    public ResponseEntity<?> bulkAssign(@RequestBody BulkAssignRequest assignRequest,
            @RequestHeader(value = "X-User-Role", required = true) String userRole) {
        if (!ROLE_ADMIN.equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String studentId = assignRequest.getUserId();
        List<Long> longCourseIds = assignRequest.getCourseIds();

        if (longCourseIds == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "courseIds list is required"));
        }

        return ResponseEntity
                .ok(Map.of("success", true, "enrollments", enrollmentService.bulkAssign(studentId, longCourseIds)));
    }
}
