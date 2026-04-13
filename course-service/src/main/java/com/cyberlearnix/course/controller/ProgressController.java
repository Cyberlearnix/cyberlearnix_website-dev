package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.enrollment.ContentProgress;
import com.cyberlearnix.shared.entity.enrollment.Enrollment;
import com.cyberlearnix.shared.entity.course.ModuleContent;
import com.cyberlearnix.shared.repository.enrollment.ContentProgressRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentRepository;
import com.cyberlearnix.shared.repository.course.ModuleContentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/courses/progress")
public class ProgressController {

    @Autowired
    private ContentProgressRepository progressRepository;

    @Autowired
    private ModuleContentRepository contentRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

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

            // Recalculate overall course progress
            Long courseId = content.getModule().getCourse().getId();
            recalculateCourseProgress(studentId, courseId);

            return ResponseEntity.ok(Map.of("success", true, "progress", progress));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<?> getCourseProgress(@PathVariable Long courseId,
            @RequestHeader("X-User-Id") String studentId) {
        List<ContentProgress> itemProgress = progressRepository.findByStudentIdAndCourseId(studentId, courseId);
        Optional<Enrollment> enrollment = enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "overallProgress", enrollment.map(Enrollment::getProgress).orElse(0),
                "items", itemProgress));
    }

    private void recalculateCourseProgress(String studentId, Long courseId) {
        Long totalContents = contentRepository.countByCourseId(courseId);
        if (totalContents == 0)
            return;

        long completedContents = progressRepository.countCompletedByStudentAndCourse(studentId, courseId);
        int percentage = (int) ((completedContents * 100) / totalContents);

        enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId).ifPresent(enrollment -> {
            enrollment.setProgress(percentage);
            if (percentage >= 100) {
                enrollment.setCompletedAt(LocalDateTime.now());
            }
            enrollmentRepository.save(enrollment);
        });
    }
}
