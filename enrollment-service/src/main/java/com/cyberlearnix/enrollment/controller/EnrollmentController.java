package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.shared.entity.enrollment.Enrollment;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentRepository;
import com.cyberlearnix.enrollment.client.CourseServiceClient;
import com.cyberlearnix.enrollment.client.UserClient;
import com.cyberlearnix.enrollment.service.EnrollmentService;
import com.cyberlearnix.enrollment.dto.*;
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

    @Autowired
    private UserClient userClient;

    @GetMapping
    public ResponseEntity<?> getEnrollments(@RequestParam(required = false) String studentId,
            @RequestParam(required = false) Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        String targetStudentId = (studentId != null) ? studentId : userId;

        // ── Admin: courseId filter → return all enrollments for that course ──────
        // Must be checked FIRST, before the generic studentId+courseId branch,
        // because X-User-Id header populates targetStudentId with the admin's own UUID
        // which would otherwise cause a spurious 404 (admin not enrolled in course).
        if (ROLE_ADMIN.equals(userRole) && courseId != null && studentId == null) {
            return ResponseEntity.ok(
                Map.of("success", true, "enrollments", enrollmentRepository.findByCourseId(courseId)));
        }

        // ── Admin: no filters → return all ──────────────────────────────────────
        if (ROLE_ADMIN.equals(userRole) && courseId == null && studentId == null) {
            return ResponseEntity.ok(Map.of("success", true, "enrollments", enrollmentRepository.findAll()));
        }

        if (targetStudentId != null && courseId != null) {
            // Return as a list so the response shape is consistent with other branches
            // and Feign clients that expect List<Enrollment>
            return enrollmentRepository.findByStudentIdAndCourseId(targetStudentId, courseId)
                    .map(e -> ResponseEntity.ok(List.of(e)))
                    .orElse(ResponseEntity.ok(List.of()));
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

    @GetMapping("/check")
    public ResponseEntity<Boolean> checkEnrollment(@RequestParam String studentId, @RequestParam Long courseId) {
        boolean isEnrolled = enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId).isPresent();
        return ResponseEntity.ok(isEnrolled);
    }

    @PatchMapping("/progress")
    public ResponseEntity<Map<String, Object>> updateProgressByStudentAndCourse(
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
            return ResponseEntity.<Map<String, Object>>ok(Map.of("success", true, "enrollment", enrollmentRepository.save(enrollment)));
        }).orElseGet(() -> ResponseEntity.<Map<String, Object>>notFound().build());
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

    // SEC-IDOR: Restrict update to admin/teacher roles, or to the enrollment owner (student updating their own record).
    @PutMapping("/{id}")
    public ResponseEntity<?> updateProgress(@PathVariable Long id,
            @RequestBody ProgressUpdateRequest progressRequest,
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestHeader(value = "X-User-Role", required = false) String callerRole) {
        return enrollmentRepository.findById(id).map(enrollment -> {
            boolean isAdmin = ROLE_ADMIN.equals(callerRole);
            boolean isTeacher = "teacher".equals(callerRole) || "dual".equals(callerRole);
            boolean isOwner = callerId != null && callerId.equals(enrollment.getStudentId());
            if (!isAdmin && !isTeacher && !isOwner) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied: you can only update your own enrollment"));
            }
            
            Integer oldProgress = enrollment.getProgress();
            if (progressRequest.getProgress() != null) {
                enrollment.setProgress(progressRequest.getProgress());
                
                // Auto-issue certificate when progress reaches 100%
                if (oldProgress == null || oldProgress < 100 && progressRequest.getProgress() >= 100) {
                    try {
                        // Fetch course title from course service
                        java.util.Map<String, Object> courseInfo = courseServiceClient.getCourseInfo(enrollment.getCourseId());
                        String courseTitle = courseInfo != null ? (String) courseInfo.get("title") : "Course";
                        
                        com.cyberlearnix.shared.entity.course.Certificate certificate = new com.cyberlearnix.shared.entity.course.Certificate();
                        certificate.setStudentId(enrollment.getStudentId());
                        certificate.setCourseId(enrollment.getCourseId());
                        certificate.setCourseTitle(courseTitle);
                        certificate.setType(com.cyberlearnix.shared.entity.course.Certificate.CertificateType.CERTIFICATE);
                        courseServiceClient.issueCertificate(certificate);
                    } catch (Exception e) {
                        // Log error but don't fail the progress update
                        System.err.println("Failed to issue certificate: " + e.getMessage());
                    }
                }
            }
            if (progressRequest.getCompletedAt() != null)
                enrollment.setCompletedAt(java.time.OffsetDateTime.parse(progressRequest.getCompletedAt()).toLocalDateTime());
            return ResponseEntity.ok(Map.of("success", true, "enrollment", enrollmentRepository.save(enrollment)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getEnrollmentById(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        return enrollmentRepository.findById(id).map(enrollment -> {
            // SEC: admin sees all; student sees only their own; teacher check deferred to business layer
            boolean isAdmin = ROLE_ADMIN.equals(userRole);
            boolean isSelf = enrollment.getStudentId() != null && enrollment.getStudentId().equals(userId);
            boolean isTeacher = "teacher".equals(userRole) || "dual".equals(userRole);
            if (!isAdmin && !isSelf && !isTeacher) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .<Object>body(Map.of("error", "Access denied"));
            }
            return ResponseEntity.ok((Object) enrollment);
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

    /**
     * GET /api/enrollments/course/{courseId}/students
     * Returns enrollments for a course enriched with student profile info (name, email, avatar).
     * Accessible by: admin, teacher/dual assigned to the course.
     */
    @GetMapping("/course/{courseId}/students")
    public ResponseEntity<?> getEnrolledStudents(
            @PathVariable Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        boolean isAdmin = ROLE_ADMIN.equals(userRole);
        boolean isTeacher = "teacher".equals(userRole) || "dual".equals(userRole);

        if (!isAdmin && !isTeacher) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
        }

        // Teachers can only see students for courses they are assigned to
        if (isTeacher && !isAdmin) {
            boolean assigned = false;
            try {
                assigned = courseServiceClient.teacherExistsForCourse(userId, courseId);
            } catch (Exception ignored) {}
            if (!assigned) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You are not assigned to this course"));
            }
        }

        List<Enrollment> enrollments = enrollmentRepository.findByCourseId(courseId);
        List<Map<String, Object>> enriched = new ArrayList<>();

        for (Enrollment enrollment : enrollments) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", enrollment.getId());
            row.put("studentId", enrollment.getStudentId());
            row.put("courseId", enrollment.getCourseId());
            row.put("progress", enrollment.getProgress() != null ? enrollment.getProgress() : 0);
            row.put("enrolledAt", enrollment.getEnrolledAt());
            row.put("completedAt", enrollment.getCompletedAt());
            row.put("started", enrollment.getProgress() != null && enrollment.getProgress() > 0);

            // Enrich with student profile from user-service
            Map<String, Object> studentProfile = new HashMap<>();
            try {
                Map<String, Object> profile = userClient.getUserProfile(enrollment.getStudentId());
                if (profile != null) {
                    studentProfile.put("full_name", profile.getOrDefault("fullName", profile.getOrDefault("full_name", "Unknown")));
                    studentProfile.put("email", profile.getOrDefault("email", ""));
                    studentProfile.put("avatar_url", profile.getOrDefault("photoUrl", profile.getOrDefault("avatar_url", null)));
                    studentProfile.put("phone", profile.getOrDefault("phone", ""));
                }
            } catch (Exception e) {
                studentProfile.put("full_name", "Student " + enrollment.getStudentId().substring(0, Math.min(6, enrollment.getStudentId().length())));
                studentProfile.put("email", "");
                studentProfile.put("avatar_url", null);
            }
            row.put("student", studentProfile);
            enriched.add(row);
        }

        return ResponseEntity.ok(Map.of("success", true, "enrollments", enriched, "total", enriched.size()));
    }

    /**
     * GET /api/enrollments/admin/course-progress?courseId={courseId}
     * Admin monitoring: which students started, completed, labs submitted.
     * Accessible by: admin only.
     */
    @GetMapping("/admin/course-progress")
    public ResponseEntity<?> getAdminCourseProgress(
            @RequestParam Long courseId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (!ROLE_ADMIN.equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        List<Enrollment> enrollments = enrollmentRepository.findByCourseId(courseId);

        long notStarted = enrollments.stream().filter(e -> e.getProgress() == null || e.getProgress() == 0).count();
        long inProgress = enrollments.stream().filter(e -> e.getProgress() != null && e.getProgress() > 0 && e.getProgress() < 100).count();
        long completed = enrollments.stream().filter(e -> e.getProgress() != null && e.getProgress() >= 100).count();

        List<Map<String, Object>> studentProgress = new ArrayList<>();
        for (Enrollment enrollment : enrollments) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", enrollment.getStudentId());
            row.put("progress", enrollment.getProgress() != null ? enrollment.getProgress() : 0);
            row.put("started", enrollment.getProgress() != null && enrollment.getProgress() > 0);
            row.put("completed", enrollment.getProgress() != null && enrollment.getProgress() >= 100);
            row.put("enrolledAt", enrollment.getEnrolledAt());
            row.put("completedAt", enrollment.getCompletedAt());

            // Enrich with student name
            try {
                Map<String, Object> profile = userClient.getUserProfile(enrollment.getStudentId());
                if (profile != null) {
                    row.put("studentName", profile.getOrDefault("fullName", profile.getOrDefault("full_name", "Unknown")));
                    row.put("studentEmail", profile.getOrDefault("email", ""));
                    row.put("studentPhone", profile.getOrDefault("phone", ""));
                    row.put("studentAvatar", profile.getOrDefault("photoUrl", profile.getOrDefault("avatar_url", "")));
                }
            } catch (Exception e) {
                row.put("studentName", "Student #" + enrollment.getId());
                row.put("studentEmail", "");
                row.put("studentPhone", "");
                row.put("studentAvatar", "");
            }
            studentProgress.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("courseId", courseId);
        summary.put("totalEnrolled", enrollments.size());
        summary.put("notStarted", notStarted);
        summary.put("inProgress", inProgress);
        summary.put("completed", completed);
        summary.put("students", studentProgress);

        return ResponseEntity.ok(Map.of("success", true, "data", summary));
    }
}
