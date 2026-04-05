package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.Course;
import com.cyberlearnix.shared.entity.course.CourseModule;
import com.cyberlearnix.shared.entity.course.ModuleContent;
import com.cyberlearnix.shared.entity.enrollment.Enrollment;
import com.cyberlearnix.shared.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Controller
public class WebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private ModuleContentRepository contentRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    // Handle authentication
    @MessageMapping("/app/auth")
    public void handleAuthentication(@Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String userRole = (String) payload.get("userRole");
        String token = (String) payload.get("token");

        // Validate token (simplified for demo)
        if (token != null && !token.isEmpty()) {
            // Send confirmation
            messagingTemplate.convertAndSendToUser(userId, "/queue/auth-status", 
                Map.of("status", "authenticated", "timestamp", LocalDateTime.now()));
        }
    }

    // Handle course updates
    @MessageMapping("/app/course/update")
    public void handleCourseUpdate(@Payload Map<String, Object> payload) {
        String courseId = (String) payload.get("courseId");
        String updateType = (String) payload.get("updateType");
        String userId = (String) payload.get("userId");

        Optional<Course> course = courseRepository.findById(Long.valueOf(courseId));
        if (course.isPresent()) {
            Map<String, Object> notification = Map.of(
                "type", "COURSE_UPDATE",
                "updateType", updateType,
                "course", course.get(),
                "updatedBy", userId,
                "timestamp", LocalDateTime.now()
            );

            // Broadcast to all subscribed users
            messagingTemplate.convertAndSend("/topic/course/" + courseId, notification);

            // Also send to general course updates topic
            messagingTemplate.convertAndSend("/topic/course-updates", notification);

            // Notify enrolled students
            notifyEnrolledStudents(Long.valueOf(courseId), notification);
        }
    }

    // Handle module updates
    @MessageMapping("/app/module/update")
    public void handleModuleUpdate(@Payload Map<String, Object> payload) {
        String moduleId = (String) payload.get("moduleId");
        String updateType = (String) payload.get("updateType");
        String userId = (String) payload.get("userId");

        Optional<CourseModule> module = moduleRepository.findById(Long.valueOf(moduleId));
        if (module.isPresent()) {
            Map<String, Object> notification = Map.of(
                "type", "MODULE_UPDATE",
                "updateType", updateType,
                "module", module.get(),
                "updatedBy", userId,
                "timestamp", LocalDateTime.now()
            );

            // Broadcast to course-specific topic
            Long courseId = module.get().getCourse().getId();
            messagingTemplate.convertAndSend("/topic/course/" + courseId, notification);

            // Also send to general module updates topic
            messagingTemplate.convertAndSend("/topic/module-updates", notification);
        }
    }

    // Handle content updates
    @MessageMapping("/app/content/update")
    public void handleContentUpdate(@Payload Map<String, Object> payload) {
        String contentId = (String) payload.get("contentId");
        String updateType = (String) payload.get("updateType");
        String userId = (String) payload.get("userId");

        Optional<ModuleContent> content = contentRepository.findById(Long.valueOf(contentId));
        if (content.isPresent()) {
            Map<String, Object> notification = Map.of(
                "type", "CONTENT_UPDATE",
                "updateType", updateType,
                "content", content.get(),
                "updatedBy", userId,
                "timestamp", LocalDateTime.now()
            );

            // Broadcast to course-specific topic
            Long courseId = content.get().getModule().getCourse().getId();
            messagingTemplate.convertAndSend("/topic/course/" + courseId, notification);

            // Also send to general content updates topic
            messagingTemplate.convertAndSend("/topic/content-updates", notification);
        }
    }

    // Handle student progress updates
    @MessageMapping("/app/student/progress")
    public void handleStudentProgress(@Payload Map<String, Object> payload) {
        String studentId = (String) payload.get("studentId");
        String courseId = (String) payload.get("courseId");
        Integer progress = (Integer) payload.get("progress");

        // Update enrollment progress in database
        Optional<Enrollment> enrollment = enrollmentRepository.findByStudentIdAndCourseId(studentId, Long.valueOf(courseId));
        if (enrollment.isPresent()) {
            Enrollment e = enrollment.get();
            e.setProgress(progress);
            if (progress >= 100) {
                e.setCompletedAt(LocalDateTime.now());
            }
            enrollmentRepository.save(e);

            Map<String, Object> notification = Map.of(
                "type", "STUDENT_PROGRESS",
                "studentId", studentId,
                "courseId", courseId,
                "progress", progress,
                "timestamp", LocalDateTime.now()
            );

            // Notify teachers assigned to this course
            notifyCourseTeachers(Long.valueOf(courseId), notification);

            // Send to student-specific queue
            messagingTemplate.convertAndSendToUser(studentId, "/queue/progress", notification);
        }
    }

    // Handle assignment updates
    @MessageMapping("/app/assignment/update")
    public void handleAssignmentUpdate(@Payload Map<String, Object> payload) {
        String assignmentId = (String) payload.get("assignmentId");
        String updateType = (String) payload.get("updateType");
        String userId = (String) payload.get("userId");

        Map<String, Object> notification = Map.of(
            "type", "ASSIGNMENT_UPDATE",
            "assignmentId", assignmentId,
            "updateType", updateType,
            "updatedBy", userId,
            "timestamp", LocalDateTime.now()
        );

        // Send to general assignment updates topic
        messagingTemplate.convertAndSend("/topic/assignment-updates", notification);

        // If it's a grading update, notify the student
        if ("GRADED".equals(updateType)) {
            String studentId = (String) payload.get("studentId");
            if (studentId != null) {
                messagingTemplate.convertAndSendToUser(studentId, "/queue/assignments", notification);
            }
        }
    }

    // Handle subscription requests
    @MessageMapping("/app/subscribe")
    public void handleSubscription(@Payload Map<String, Object> payload) {
        String topic = (String) payload.get("topic");
        String userId = (String) payload.get("userId");

        // Send confirmation
        messagingTemplate.convertAndSendToUser(userId, "/queue/subscription", 
            Map.of("topic", topic, "status", "subscribed", "timestamp", LocalDateTime.now()));
    }

    // Handle unsubscription requests
    @MessageMapping("/app/unsubscribe")
    public void handleUnsubscription(@Payload Map<String, Object> payload) {
        String topic = (String) payload.get("topic");
        String userId = (String) payload.get("userId");

        // Send confirmation
        messagingTemplate.convertAndSendToUser(userId, "/queue/subscription", 
            Map.of("topic", topic, "status", "unsubscribed", "timestamp", LocalDateTime.now()));
    }

    // Helper methods
    private void notifyEnrolledStudents(Long courseId, Map<String, Object> notification) {
        // Get all enrolled students for this course
        enrollmentRepository.findByCourseId(courseId).forEach(enrollment -> {
            messagingTemplate.convertAndSendToUser(enrollment.getStudentId(), "/queue/course-updates", notification);
        });
    }

    private void notifyCourseTeachers(Long courseId, Map<String, Object> notification) {
        // Get all teachers assigned to this course
        // This would require a CourseTeacherRepository to find teachers by course
        // For now, we'll send to a general teacher notifications topic
        messagingTemplate.convertAndSend("/topic/teacher-notifications", notification);
    }

    // Error handling
    @MessageExceptionHandler
    public void handleException(Exception exception) {
        System.err.println("WebSocket error: " + exception.getMessage());
        // Send error to user if possible
        messagingTemplate.convertAndSend("/topic/errors", 
            Map.of("error", exception.getMessage(), "timestamp", LocalDateTime.now()));
    }
}
