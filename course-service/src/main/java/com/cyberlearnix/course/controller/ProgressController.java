package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.Certificate;
import com.cyberlearnix.shared.entity.course.ContentProgress;
import com.cyberlearnix.shared.entity.course.Course;
import com.cyberlearnix.shared.entity.course.ModuleContent;
import com.cyberlearnix.shared.repository.course.CertificateRepository;
import com.cyberlearnix.shared.repository.course.ContentProgressRepository;
import com.cyberlearnix.shared.repository.course.CourseRepository;
import com.cyberlearnix.shared.repository.course.ModuleContentRepository;
import com.cyberlearnix.course.client.EnrollmentServiceClient;
import com.cyberlearnix.course.client.UserServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses/progress")
public class ProgressController {

    @Autowired
    private ContentProgressRepository progressRepository;

    @Autowired
    private ModuleContentRepository contentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private EnrollmentServiceClient enrollmentServiceClient;

    @Autowired
    private UserServiceClient userServiceClient;

    @PostMapping("/update")
    public ResponseEntity<?> updateProgress(@RequestBody Map<String, Object> payload,
            @RequestHeader("X-User-Id") String studentId) {
        Long contentId = Long.valueOf(payload.get("contentId").toString());
        String status = (String) payload.get("status"); // STARTED, COMPLETED
        Integer videoTime = payload.containsKey("videoTime") ? (Integer) payload.get("videoTime") : null;
        Double score = payload.containsKey("score") ? Double.valueOf(payload.get("score").toString()) : null;

        return contentRepository.findById(contentId).map(content -> {
            ContentProgress progress = progressRepository.findByStudentIdAndContentId(studentId, contentId)
                    .orElse(new ContentProgress());

            progress.setStudentId(studentId);
            progress.setContentId(contentId);
            progress.setLastAccessedAt(LocalDateTime.now());

            if (status != null) {
                progress.setStatus(status);
                if ("COMPLETED".equalsIgnoreCase(status)) {
                    progress.setIsCompleted(true);
                    progress.setCompletedAt(LocalDateTime.now());
                }
            }

            if (videoTime != null) {
                progress.setVideoTimeSeconds(videoTime);
            }

            if (score != null) {
                progress.setScore(score);
            }

            progressRepository.save(progress);

            // Recalculate overall course progress via enrollment-service
            Long courseId = content.getModule().getCourse().getId();
            recalculateCourseProgress(studentId, courseId);

            return ResponseEntity.ok(Map.of("success", true, "progress", progress));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<?> getCourseProgress(@PathVariable Long courseId,
            @RequestHeader("X-User-Id") String studentId) {
        List<ContentProgress> itemProgress = progressRepository.findByStudentIdAndCourseId(studentId, courseId);

        // Get overall progress from enrollment-service
        int overallProgress = 0;
        try {
            List<Map<String, Object>> enrollments = enrollmentServiceClient.getEnrollments(studentId, courseId);
            if (!enrollments.isEmpty()) {
                Object p = enrollments.get(0).get("progress");
                overallProgress = p != null ? ((Number) p).intValue() : 0;
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "overallProgress", overallProgress,
                "items", itemProgress));
    }

    private void recalculateCourseProgress(String studentId, Long courseId) {
        Long totalContents = contentRepository.countByCourseId(courseId);
        if (totalContents == 0)
            return;

        long completedContents = progressRepository.countCompletedByStudentAndCourse(studentId, courseId);
        int percentage = (int) ((completedContents * 100) / totalContents);

        try {
            Map<String, Object> progressUpdate = new HashMap<>();
            progressUpdate.put("studentId", studentId);
            progressUpdate.put("courseId", courseId);
            progressUpdate.put("progress", percentage);
            if (percentage >= 100) {
                progressUpdate.put("completedAt", LocalDateTime.now().toString());
            }
            enrollmentServiceClient.updateProgress(progressUpdate);
        } catch (Exception ignored) {
        }

        // Auto-issue certificate when course is completed (100%) if certificate is enabled
        if (percentage >= 100) {
            try {
                courseRepository.findById(courseId).ifPresent(course -> {
                    if (Boolean.TRUE.equals(course.getCertificateEnabled())) {
                        // Only issue once — skip if already issued
                        boolean alreadyIssued = certificateRepository
                                .findByStudentIdAndCourseId(studentId, courseId)
                                .isPresent();
                        if (!alreadyIssued) {
                            String studentName = resolveStudentName(studentId);
                            Certificate cert = new Certificate();
                            cert.setStudentId(studentId);
                            cert.setStudentName(studentName);
                            cert.setCourseId(courseId);
                            cert.setCourseTitle(course.getTitle());
                            cert.setInstructorName(course.getInstructorName() != null
                                    ? course.getInstructorName()
                                    : "CyberLearnix Instructor");
                            cert.setCertificateImageUrl(course.getCertificateImageUrl());
                            cert.setType(Certificate.CertificateType.CERTIFICATE);
                            cert.setIssuedAt(LocalDateTime.now());
                            cert.setCreatedAt(LocalDateTime.now());
                            cert.setCertificateId("CLX-" + courseId + "-" + studentId.replaceAll("[^a-zA-Z0-9]", "").substring(0, Math.min(8, studentId.length())) + "-" + System.currentTimeMillis());
                            certificateRepository.save(cert);
                        }
                    }
                });
            } catch (Exception e) {
                // Certificate issuance failure must NOT break progress update
                System.err.println("[CertificateAutoIssue] Failed for student=" + studentId + " course=" + courseId + ": " + e.getMessage());
            }
        }
    }

    private String resolveStudentName(String studentId) {
        try {
            Map<String, Object> profile = userServiceClient.getUserProfile(studentId);
            if (profile == null) return studentId;
            Object name = profile.get("fullName");
            if (name == null) name = profile.get("full_name");
            if (name == null) name = profile.get("name");
            return name != null ? name.toString() : studentId;
        } catch (Exception e) {
            return studentId;
        }
    }
}
