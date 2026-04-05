package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.Course;
import com.cyberlearnix.shared.entity.course.CourseModule;
import com.cyberlearnix.shared.entity.course.ModuleContent;
import com.cyberlearnix.shared.entity.course.CourseTeacher;
import com.cyberlearnix.shared.entity.course.CourseTeacherId;
import com.cyberlearnix.shared.entity.course.ContentPartner;
import com.cyberlearnix.shared.entity.course.CourseSuggestion;
import com.cyberlearnix.shared.entity.course.LabContent;
import com.cyberlearnix.shared.entity.course.LectureContent;
import com.cyberlearnix.shared.entity.course.QuizContent;
import com.cyberlearnix.shared.entity.course.QuizQuestion;
import com.cyberlearnix.shared.entity.course.QuestionOption;
import com.cyberlearnix.shared.entity.course.AssignmentContent;
import com.cyberlearnix.shared.entity.course.ContentReview;
import com.cyberlearnix.shared.entity.course.ContentUpdate;
import com.cyberlearnix.shared.entity.course.Banner;
import com.cyberlearnix.shared.entity.course.PromoBanner;
import com.cyberlearnix.shared.entity.user.User;
import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.entity.user.TeacherPermission;
import com.cyberlearnix.shared.entity.enrollment.Enrollment;
import com.cyberlearnix.shared.entity.enrollment.ContentProgress;
import com.cyberlearnix.shared.repository.*;
import com.cyberlearnix.course.service.CourseManagementService;
import com.cyberlearnix.course.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/course-management")
public class CourseManagementController {
// ... existing autowired fields ...
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private ModuleContentRepository contentRepository;

    @Autowired
    private LabContentRepository labRepository;

    @Autowired
    private AssignmentContentRepository assignmentRepository;

    @Autowired
    private TeacherPermissionRepository permissionRepository;

    @Autowired
    private LectureContentRepository lectureRepository;

    @Autowired
    private QuizContentRepository quizRepository;

    @Autowired
    private QuizQuestionRepository questionRepository;

    @Autowired
    private QuestionOptionRepository optionRepository;

    @Autowired
    private CourseManagementService courseService;

    @Autowired
    private CourseTeacherRepository courseTeacherRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    // Course CRUD Operations
    @PostMapping("/courses")
    public ResponseEntity<?> createCourse(@RequestBody CourseCreateDTO courseDTO,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"admin".equalsIgnoreCase(userRole)) {
            TeacherPermission permission = permissionRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Teacher permissions not found"));

            if (!permission.getCanCreateCourses()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You don't have permission to create courses"));
            }
        }

        Course course = new Course();
        course.setTitle(courseDTO.getTitle());
        course.setDescription(courseDTO.getDescription());
        course.setContentUrl(courseDTO.getContentUrl());
        course.setThumbnailUrl(courseDTO.getThumbnailUrl());
        course.setBasePrice(courseDTO.getBasePrice());
        
        if (courseDTO.getGstPercent() != null) {
            course.setGstPercent(courseDTO.getGstPercent());
        }
        if (courseDTO.getFinalPrice() != null) {
            course.setFinalPrice(courseDTO.getFinalPrice());
        }
        
        course.setCategory(courseDTO.getCategory());
        course.setDifficultyLevel(courseDTO.getDifficultyLevel());
        course.setDuration(courseDTO.getDuration());
        course.setActive(courseDTO.getIsActive() != null ? courseDTO.getIsActive() : true);

        course.setCreatedBy(userId);
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());

        Course savedCourse = courseRepository.save(course);
        
        // AUTO-ASSIGN creator as teacher so they can edit it
        CourseTeacher ct = new CourseTeacher();
        ct.setCourseId(savedCourse.getId());
        ct.setTeacherId(userId);
        courseTeacherRepository.save(ct);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "course", savedCourse));
    }

    @PutMapping("/courses/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable Long id,
            @RequestBody CourseUpdateDTO courseDTO,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        return courseRepository.findById(id).map(existingCourse -> {
            if (!"admin".equalsIgnoreCase(userRole)) {
                // SECURITY CHECK: Is this teacher assigned to this course?
                if (!courseTeacherRepository.existsByCourseIdAndTeacherId(id, userId) && !userId.equals(existingCourse.getCreatedBy())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You are not assigned to this course."));
                }

                TeacherPermission permission = permissionRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Teacher permissions not found"));

                if (!permission.getCanEditCourses()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to edit courses"));
                }
            }

            // Update course fields
            if (courseDTO.getTitle() != null) existingCourse.setTitle(courseDTO.getTitle());
            if (courseDTO.getDescription() != null) existingCourse.setDescription(courseDTO.getDescription());
            if (courseDTO.getContentUrl() != null) existingCourse.setContentUrl(courseDTO.getContentUrl());
            if (courseDTO.getThumbnailUrl() != null) existingCourse.setThumbnailUrl(courseDTO.getThumbnailUrl());
            if (courseDTO.getBasePrice() != null) existingCourse.setBasePrice(courseDTO.getBasePrice());
            if (courseDTO.getGstPercent() != null) existingCourse.setGstPercent(courseDTO.getGstPercent());
            if (courseDTO.getFinalPrice() != null) existingCourse.setFinalPrice(courseDTO.getFinalPrice());
            if (courseDTO.getCategory() != null) existingCourse.setCategory(courseDTO.getCategory());
            if (courseDTO.getDifficultyLevel() != null) existingCourse.setDifficultyLevel(courseDTO.getDifficultyLevel());
            if (courseDTO.getDuration() != null) existingCourse.setDuration(courseDTO.getDuration());
            if (courseDTO.getIsActive() != null) existingCourse.setActive(courseDTO.getIsActive());
            existingCourse.setUpdatedAt(LocalDateTime.now());

            Course savedCourse = courseRepository.save(existingCourse);
            return ResponseEntity.ok(Map.of("success", true, "course", savedCourse));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        return courseRepository.findById(id).map(course -> {
            if (!"admin".equalsIgnoreCase(userRole)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only administrators can delete courses"));
            }

            courseRepository.delete(course);
            return ResponseEntity.ok(Map.of("success", true, "message", "Course deleted successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Module Management
    @PostMapping("/courses/{courseId}/modules")
    public ResponseEntity<?> createModule(@PathVariable Long courseId,
            @RequestBody ModuleCreateDTO moduleDTO,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        return courseRepository.findById(courseId).map(course -> {
            if (!"admin".equalsIgnoreCase(userRole)) {
                // SECURITY CHECK: Is this teacher assigned to this course?
                if (!courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You are not assigned to this course. Please contact admin."));
                }

                TeacherPermission permission = permissionRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Teacher permissions not found"));

                if (!permission.getCanAddModules()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to add modules"));
                }

                Long moduleCount = moduleRepository.countByCourseId(courseId);
                if (moduleCount >= permission.getMaxModulesPerCourse()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Maximum module limit reached for this course"));
                }
            }

            CourseModule module = new CourseModule();
            module.setTitle(moduleDTO.getTitle());
            module.setDescription(moduleDTO.getDescription());
            module.setOrderIndex(moduleDTO.getOrderIndex());
            module.setCourse(course);
            module.setCreatedBy(userId);
            module.setCreatedAt(LocalDateTime.now());
            module.setUpdatedAt(LocalDateTime.now());

            // Set order index if not provided
            if (module.getOrderIndex() == null || module.getOrderIndex() == 0) {
                Integer maxOrder = moduleRepository.findMaxOrderIndexByCourseId(courseId);
                module.setOrderIndex(maxOrder != null ? maxOrder + 1 : 1);
            }

            CourseModule savedModule = moduleRepository.save(module);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("success", true, "module", savedModule));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Content Management
    @PostMapping("/modules/{moduleId}/contents")
    public ResponseEntity<?> createContent(@PathVariable Long moduleId,
            @RequestBody ContentCreateDTO contentDTO,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        return moduleRepository.findById(moduleId).map(module -> {
            if (!"admin".equalsIgnoreCase(userRole)) {
                TeacherPermission permission = permissionRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Teacher permissions not found"));

                if (!permission.getCanAddContent()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to add content"));
                }

                // Check exam permission
                String type = contentDTO.getContentType();
                if (("QUIZ".equalsIgnoreCase(type) || "EXAM".equalsIgnoreCase(type))
                        && !permission.getCanManageExams()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to manage exams/quizzes"));
                }

                // SECURITY CHECK: Is this teacher assigned to this course?
                Long courseId = module.getCourse().getId();
                if (!courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You are not assigned to this course."));
                }

                // Check content limit
                Long contentCount = contentRepository.countByModuleId(moduleId);
                if (contentCount >= permission.getMaxContentPerModule()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Maximum content limit reached for this module"));
                }
            }

            ModuleContent content;
            String type = contentDTO.getContentType();
            if ("LAB".equals(type)) {
                LabContent lab = new LabContent();
                lab.setLabType(contentDTO.getLabType());
                lab.setInstructions(contentDTO.getInstructions());
                lab.setEnvironmentConfig(contentDTO.getEnvironmentConfig());
                lab.setDurationMinutes(contentDTO.getDurationMinutes());
                content = lab;
            } else if ("ASSIGNMENT".equals(type)) {
                AssignmentContent ass = new AssignmentContent();
                ass.setAssignmentType(contentDTO.getAssignmentType());
                ass.setInstructions(contentDTO.getInstructions());
                ass.setMaxScore(contentDTO.getMaxScore());
                content = ass;
            } else if ("LECTURE".equals(type) || "VIDEO".equals(type)) {
                LectureContent lect = new LectureContent();
                lect.setVideoUrl(contentDTO.getVideoUrl());
                lect.setContentText(contentDTO.getContentText());
                lect.setIsPreview(contentDTO.getIsPreview() != null ? contentDTO.getIsPreview() : false);
                lect.setAttachmentUrl(contentDTO.getAttachmentUrl());
                lect.setDurationMinutes(contentDTO.getDurationMinutes());
                content = lect;
            } else if ("QUIZ".equals(type) || "EXAM".equals(type)) {
                QuizContent quiz = new QuizContent();
                quiz.setQuizId(contentDTO.getQuizId());
                quiz.setTimeLimitMinutes(contentDTO.getTimeLimitMinutes());
                quiz.setPassingScore(contentDTO.getPassingScore());
                quiz.setMaxAttempts(contentDTO.getMaxAttempts());
                content = quiz;
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid or unsupported content type: " + type));
            }

            content.setTitle(contentDTO.getTitle());
            content.setDescription(contentDTO.getDescription());
            content.setContentType(type);
            content.setOrderIndex(contentDTO.getOrderIndex());
            content.setModule(module);
            content.setCreatedBy(userId);
            content.setCreatedAt(LocalDateTime.now());
            content.setUpdatedAt(LocalDateTime.now());

            // Set order index if not provided
            if (content.getOrderIndex() == null || content.getOrderIndex() == 0) {
                Integer maxOrder = contentRepository.findMaxOrderIndexByModuleId(moduleId);
                content.setOrderIndex(maxOrder != null ? maxOrder + 1 : 1);
            }

            ModuleContent savedContent;
            if (content instanceof LabContent) {
                savedContent = labRepository.save((LabContent) content);
            } else if (content instanceof AssignmentContent) {
                savedContent = assignmentRepository.save((AssignmentContent) content);
            } else if (content instanceof LectureContent) {
                savedContent = lectureRepository.save((LectureContent) content);
            } else if (content instanceof QuizContent) {
                savedContent = quizRepository.save((QuizContent) content);
            } else {
                savedContent = contentRepository.save(content);
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("success", true, "content", savedContent));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Quiz Question Management (NetAcad Style Assessments)
    @PostMapping("/contents/{contentId}/quiz/questions")
    public ResponseEntity<?> addQuestions(@PathVariable Long contentId,
            @RequestBody List<QuizQuestionDTO> questionDTOs,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"admin".equalsIgnoreCase(userRole) && !"teacher".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        return quizRepository.findById(contentId).map(quiz -> {
            for (QuizQuestionDTO qDto : questionDTOs) {
                QuizQuestion q = new QuizQuestion();
                q.setQuestionText(qDto.getQuestionText());
                q.setQuestionType(qDto.getQuestionType());
                q.setPoints(qDto.getPoints());
                q.setExplanation(qDto.getExplanation());
                q.setQuiz(quiz);
                
                QuizQuestion savedQ = questionRepository.save(q);
                if (qDto.getOptions() != null) {
                    for (QuestionOptionDTO optDto : qDto.getOptions()) {
                        QuestionOption opt = new QuestionOption();
                        opt.setOptionText(optDto.getOptionText());
                        opt.setIsCorrect(optDto.getIsCorrect());
                        opt.setQuestion(savedQ);
                        optionRepository.save(opt);
                    }
                }
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("success", true, "count", questionDTOs.size()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/modules/{id}")
    public ResponseEntity<?> updateModule(@PathVariable Long id,
            @RequestBody ModuleCreateDTO moduleUpdate,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        return moduleRepository.findById(id).map(module -> {
            if (!"admin".equalsIgnoreCase(userRole)) {
                // SECURITY CHECK: Is this teacher assigned to this course?
                Long courseId = module.getCourse().getId();
                if (!courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You are not assigned to this course."));
                }

                TeacherPermission permission = permissionRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Teacher permissions not found"));

                if (!permission.getCanEditModules()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to edit modules"));
                }
            }

            if (moduleUpdate.getTitle() != null)
                module.setTitle(moduleUpdate.getTitle());
            if (moduleUpdate.getDescription() != null)
                module.setDescription(moduleUpdate.getDescription());
            if (moduleUpdate.getOrderIndex() != null)
                module.setOrderIndex(moduleUpdate.getOrderIndex());
            module.setUpdatedAt(LocalDateTime.now());

            CourseModule savedModule = moduleRepository.save(module);
            return ResponseEntity.ok(Map.of("success", true, "module", savedModule));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/contents/{id}")
    public ResponseEntity<?> updateContent(@PathVariable Long id,
            @RequestBody ContentCreateDTO contentDTO,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        return contentRepository.findById(id).map(content -> {
            if (!"admin".equalsIgnoreCase(userRole)) {
                // SECURITY CHECK: Is this teacher assigned to this course?
                Long courseId = content.getModule().getCourse().getId();
                if (!courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You are not assigned to this course."));
                }

                TeacherPermission permission = permissionRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Teacher permissions not found"));

                if (!permission.getCanEditContent()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to edit content"));
                }

                // Check exam permission
                String type = content.getContentType();
                if (("QUIZ".equalsIgnoreCase(type) || "EXAM".equalsIgnoreCase(type))
                        && !permission.getCanManageExams()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to manage exams/quizzes"));
                }
            }

            if (contentDTO.getTitle() != null)
                content.setTitle(contentDTO.getTitle());
            if (contentDTO.getDescription() != null)
                content.setDescription(contentDTO.getDescription());
            if (contentDTO.getOrderIndex() != null)
                content.setOrderIndex(contentDTO.getOrderIndex());
            content.setUpdatedAt(LocalDateTime.now());

            ModuleContent savedContent;
            String type = content.getContentType();
            if ("LAB".equals(type) && content instanceof LabContent) {
                LabContent lab = (LabContent) content;
                if (contentDTO.getLabType() != null) lab.setLabType(contentDTO.getLabType());
                if (contentDTO.getInstructions() != null) lab.setInstructions(contentDTO.getInstructions());
                if (contentDTO.getEnvironmentConfig() != null) lab.setEnvironmentConfig(contentDTO.getEnvironmentConfig());
                if (contentDTO.getDurationMinutes() != null) lab.setDurationMinutes(contentDTO.getDurationMinutes());
                savedContent = labRepository.save(lab);
            } else if ("ASSIGNMENT".equals(type) && content instanceof AssignmentContent) {
                AssignmentContent ass = (AssignmentContent) content;
                if (contentDTO.getAssignmentType() != null) ass.setAssignmentType(contentDTO.getAssignmentType());
                if (contentDTO.getInstructions() != null) ass.setInstructions(contentDTO.getInstructions());
                if (contentDTO.getMaxScore() != null) ass.setMaxScore(contentDTO.getMaxScore());
                savedContent = assignmentRepository.save(ass);
            } else if (("LECTURE".equals(type) || "VIDEO".equals(type)) && content instanceof LectureContent) {
                LectureContent lect = (LectureContent) content;
                if (contentDTO.getVideoUrl() != null) lect.setVideoUrl(contentDTO.getVideoUrl());
                if (contentDTO.getContentText() != null) lect.setContentText(contentDTO.getContentText());
                if (contentDTO.getDurationMinutes() != null) lect.setDurationMinutes(contentDTO.getDurationMinutes());
                if (contentDTO.getIsPreview() != null) lect.setIsPreview(contentDTO.getIsPreview());
                if (contentDTO.getAttachmentUrl() != null) lect.setAttachmentUrl(contentDTO.getAttachmentUrl());
                savedContent = lectureRepository.save(lect);
            } else if (("QUIZ".equals(type) || "EXAM".equals(type)) && content instanceof QuizContent) {
                QuizContent quiz = (QuizContent) content;
                if (contentDTO.getQuizId() != null) quiz.setQuizId(contentDTO.getQuizId());
                if (contentDTO.getTimeLimitMinutes() != null) quiz.setTimeLimitMinutes(contentDTO.getTimeLimitMinutes());
                if (contentDTO.getPassingScore() != null) quiz.setPassingScore(contentDTO.getPassingScore());
                if (contentDTO.getMaxAttempts() != null) quiz.setMaxAttempts(contentDTO.getMaxAttempts());
                savedContent = quizRepository.save(quiz);
            } else {
                savedContent = contentRepository.save(content);
            }

            return ResponseEntity.ok(Map.of("success", true, "content", savedContent));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Get course with all modules and content
    @GetMapping("/courses/{id}/full")
    public ResponseEntity<?> getCourseWithContent(@PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"admin".equalsIgnoreCase(userRole) && !"teacher".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Course> course = courseRepository.findById(id);
        if (course.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<CourseModule> modules = moduleRepository.findByCourseIdOrderByOrderIndex(id);

        Map<String, Object> response = Map.of(
                "success", true,
                "course", course.get(),
                "modules", modules);

        return ResponseEntity.ok(response);
    }

    // Get teacher permissions
    @GetMapping("/teacher-permissions/{teacherId}")
    public ResponseEntity<?> getTeacherPermissions(@PathVariable String teacherId,
            @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        TeacherPermission permissions = permissionRepository.findById(teacherId)
                .orElse(new TeacherPermission());

        return ResponseEntity.ok(Map.of("success", true, "permissions", permissions));
    }

    // Update teacher permissions
    @PutMapping("/teacher-permissions/{teacherId}")
    public ResponseEntity<?> updateTeacherPermissions(@PathVariable String teacherId,
            @Valid @RequestBody TeacherPermission permissions,
            @RequestHeader("X-User-Id") String adminId,
            @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        permissions.setTeacherId(teacherId);
        permissions.setGrantedBy(adminId);
        permissions.setUpdatedAt(LocalDateTime.now());

        TeacherPermission saved = permissionRepository.save(permissions);
        return ResponseEntity.ok(Map.of("success", true, "permissions", saved));
    }

    // Delete module
    @DeleteMapping("/modules/{id}")
    public ResponseEntity<?> deleteModule(@PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        return moduleRepository.findById(id).map(module -> {
            if (!"admin".equalsIgnoreCase(userRole)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only administrators can delete modules"));
            }

            moduleRepository.delete(module);
            return ResponseEntity.ok(Map.of("success", true, "message", "Module deleted successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Delete content
    @DeleteMapping("/contents/{id}")
    public ResponseEntity<?> deleteContent(@PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        return contentRepository.findById(id).map(content -> {
            if (!"admin".equalsIgnoreCase(userRole)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only administrators can delete content"));
            }

            contentRepository.delete(content);
            return ResponseEntity.ok(Map.of("success", true, "message", "Content deleted successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Get all contents of a specific module
    @GetMapping("/modules/{moduleId}/contents")
    public ResponseEntity<?> getModuleContents(@PathVariable Long moduleId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"admin".equalsIgnoreCase(userRole) && !"teacher".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return moduleRepository.findById(moduleId).map(module -> {
            List<ModuleContent> contents = contentRepository.findByModuleIdOrderByOrderIndex(moduleId);
            return ResponseEntity.ok(Map.of("success", true, "moduleTitle", module.getTitle(), "contents", contents));
        }).orElse(ResponseEntity.notFound().build());
    }

    // New Endpoint for Teacher Dashboard: Student Monitoring
    @GetMapping("/courses/{id}/students")
    public ResponseEntity<?> getEnrolledStudents(@PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        // Check if user is admin or assigned teacher
        if (!"admin".equalsIgnoreCase(userRole)) {
            if (!courseTeacherRepository.existsByCourseIdAndTeacherId(id, userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied. You are not assigned to this course."));
            }
        }

        List<Enrollment> enrollments = enrollmentRepository.findByCourseId(id);
        
        List<Map<String, Object>> students = enrollments.stream().map(enrollment -> {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("studentId", enrollment.getStudentId());
            data.put("progress", enrollment.getProgress());
            data.put("enrolledAt", enrollment.getEnrolledAt());
            data.put("completedAt", enrollment.getCompletedAt());
            data.put("status", enrollment.getCompletedAt() != null ? "COMPLETED" : "IN_PROGRESS");

            // Fetch name and email from user-profile via repository (assuming it's available)
            userProfileRepository.findById(enrollment.getStudentId()).ifPresent(profile -> {
                data.put("fullName", profile.getFullName());
                data.put("email", profile.getEmail());
            });

            return data;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "courseId", id,
            "studentCount", students.size(),
            "students", students
        ));
    }
}
