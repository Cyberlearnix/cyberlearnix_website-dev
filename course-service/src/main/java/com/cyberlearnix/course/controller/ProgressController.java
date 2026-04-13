package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.ContentProgress;
import com.cyberlearnix.shared.entity.course.ModuleContent;
import com.cyberlearnix.shared.repository.course.ContentProgressRepository;
import com.cyberlearnix.shared.repository.course.ModuleContentRepository;
import com.cyberlearnix.course.client.EnrollmentServiceClient;
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
    private EnrollmentServiceClient enrollmentServiceClient;

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
    }
}
