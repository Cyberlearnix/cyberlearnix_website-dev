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
import com.cyberlearnix.shared.entity.course.LiveSessionContent;
import com.cyberlearnix.shared.repository.course.*;
import com.cyberlearnix.course.client.EnrollmentServiceClient;
import com.cyberlearnix.course.client.UserServiceClient;
import com.cyberlearnix.course.service.CourseManagementService;
import com.cyberlearnix.course.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/course-management")
public class CourseManagementController {

    private static final String NOT_ASSIGNED_MSG = "You are not assigned to this course.";

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
    private LectureContentRepository lectureRepository;

    @Autowired
    private QuizContentRepository quizRepository;

    @Autowired
    private QuizQuestionRepository questionRepository;

    @Autowired
    private QuestionOptionRepository optionRepository;

    @Autowired
    private LiveSessionContentRepository liveSessionRepository;

    @Autowired
    private CourseManagementService courseService;

    @Autowired
    private CourseTeacherRepository courseTeacherRepository;

    @Autowired
    private EnrollmentServiceClient enrollmentServiceClient;

    @Autowired
    private UserServiceClient userServiceClient;

    // Course CRUD Operations
    @Transactional
    @PostMapping("/courses")
    public ResponseEntity<?> createCourse(@Valid @RequestBody CourseCreateDTO courseDTO,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"admin".equalsIgnoreCase(userRole)) {
            try {
                Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
                if (perm == null || !Boolean.TRUE.equals(perm.get("canCreateCourses"))) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to create courses"));
                }
            } catch (Exception ex) {
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
        if (courseDTO.getCertificateEnabled() != null) course.setCertificateEnabled(courseDTO.getCertificateEnabled());
        if (courseDTO.getInstructorName() != null) course.setInstructorName(courseDTO.getInstructorName());
        if (courseDTO.getCertificateImageUrl() != null) course.setCertificateImageUrl(courseDTO.getCertificateImageUrl());

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
                            .body(Map.of("error", NOT_ASSIGNED_MSG));
                }

                try {
                    Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
                    if (!Boolean.TRUE.equals(perm.get("canEditCourses"))) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "You don't have permission to edit courses"));
                    }
                } catch (Exception ex) {
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
            if (courseDTO.getCertificateEnabled() != null) existingCourse.setCertificateEnabled(courseDTO.getCertificateEnabled());
            if (courseDTO.getInstructorName() != null) existingCourse.setInstructorName(courseDTO.getInstructorName());
            if (courseDTO.getCertificateImageUrl() != null) existingCourse.setCertificateImageUrl(courseDTO.getCertificateImageUrl());
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

                try {
                    Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
                    if (!Boolean.TRUE.equals(perm.get("canAddModules"))) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "You don't have permission to add modules"));
                    }
                    Object maxMod = perm.get("maxModulesPerCourse");
                    if (maxMod != null) {
                        Long moduleCount = moduleRepository.countByCourseId(courseId);
                        if (moduleCount >= ((Number) maxMod).longValue()) {
                            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body(Map.of("error", "Maximum module limit reached for this course"));
                        }
                    }
                } catch (Exception ex) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to add modules"));
                }
            }

            CourseModule module = new CourseModule();
            module.setTitle(moduleDTO.getTitle());
            module.setDescription(moduleDTO.getDescription());
            module.setImageUrl(moduleDTO.getImageUrl());
            module.setOrderIndex(moduleDTO.getOrderIndex());
            module.setCourse(course);
            module.setCreatedBy(userId);
            module.setCreatedAt(LocalDateTime.now());
            module.setUpdatedAt(LocalDateTime.now());

            // Optionally nest under a parent module (sub-chapter)
            if (moduleDTO.getParentModuleId() != null) {
                moduleRepository.findById(moduleDTO.getParentModuleId())
                        .ifPresent(module::setParentModule);
            }

            // Set order index if not provided
            if (module.getOrderIndex() == null || module.getOrderIndex() == 0) {
                if (moduleDTO.getParentModuleId() != null) {
                    Integer maxOrder = moduleRepository.findMaxOrderIndexByParentId(moduleDTO.getParentModuleId());
                    module.setOrderIndex(maxOrder != null ? maxOrder + 1 : 1);
                } else {
                    Integer maxOrder = moduleRepository.findMaxOrderIndexByCourseId(courseId);
                    module.setOrderIndex(maxOrder != null ? maxOrder + 1 : 1);
                }
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
                ResponseEntity<Map<String, Object>> denied = checkTeacherContentPermission(module, moduleId, userId, contentDTO);
                if (denied != null) return denied;
            }

            ModuleContent content = buildTypedContent(contentDTO);
            if (content == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid or unsupported content type: " + contentDTO.getContentType()));
            }

            content.setTitle(contentDTO.getTitle());
            content.setDescription(contentDTO.getDescription());
            content.setContentType(contentDTO.getContentType());
            content.setOrderIndex(contentDTO.getOrderIndex());
            content.setModule(module);
            content.setCreatedBy(userId);
            content.setCreatedAt(LocalDateTime.now());
            content.setUpdatedAt(LocalDateTime.now());
            copySettingsFields(contentDTO, content);

            if (content.getOrderIndex() == null || content.getOrderIndex() == 0) {
                Integer maxOrder = contentRepository.findMaxOrderIndexByModuleId(moduleId);
                content.setOrderIndex(maxOrder != null ? maxOrder + 1 : 1);
            }

            ModuleContent savedContent = persistContent(content);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("success", true, "content", toContentResponse(savedContent)));
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
                            .body(Map.of("error", NOT_ASSIGNED_MSG));
                }

                try {
                    Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
                    if (!Boolean.TRUE.equals(perm.get("canEditModules"))) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "You don't have permission to edit modules"));
                    }
                } catch (Exception ex) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to edit modules"));
                }
            }

            if (moduleUpdate.getTitle() != null)
                module.setTitle(moduleUpdate.getTitle());
            if (moduleUpdate.getDescription() != null)
                module.setDescription(moduleUpdate.getDescription());
            if (moduleUpdate.getImageUrl() != null)
                module.setImageUrl(moduleUpdate.getImageUrl());
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
                            .body(Map.of("error", NOT_ASSIGNED_MSG));
                }

                try {
                    Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
                    if (!Boolean.TRUE.equals(perm.get("canEditContent"))) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "You don't have permission to edit content"));
                    }
                    // Check exam permission
                    String type = content.getContentType();
                    if (("QUIZ".equalsIgnoreCase(type) || "EXAM".equalsIgnoreCase(type))
                            && !Boolean.TRUE.equals(perm.get("canManageExams"))) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "You don't have permission to manage exams/quizzes"));
                    }
                } catch (Exception ex) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to edit content"));
                }
            }

            if (contentDTO.getTitle() != null)
                content.setTitle(contentDTO.getTitle());
            if (contentDTO.getDescription() != null)
                content.setDescription(contentDTO.getDescription());
            if (contentDTO.getOrderIndex() != null)
                content.setOrderIndex(contentDTO.getOrderIndex());
            content.setUpdatedAt(LocalDateTime.now());
            copySettingsFields(contentDTO, content);

            ModuleContent savedContent;
            String type = content.getContentType();
            if ("LAB".equals(type) && content instanceof LabContent) {
                LabContent lab = (LabContent) content;
                if (contentDTO.getLabType() != null) lab.setLabType(contentDTO.getLabType());
                if (contentDTO.getInstructions() != null) lab.setInstructions(contentDTO.getInstructions());
                if (contentDTO.getEnvironmentConfig() != null) {
                    lab.setEnvironmentConfig(contentDTO.getEnvironmentConfig());
                    try {
                        Map<String, Object> config = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(contentDTO.getEnvironmentConfig(), Map.class);
                        if (config.get("difficulty") != null) lab.setDifficultyLevel(config.get("difficulty").toString());
                        if (config.get("prerequisites") != null) lab.setPrerequisites(config.get("prerequisites").toString());
                        if (config.get("objectives") != null) lab.setLearningObjectives(config.get("objectives").toString());
                    } catch (Exception ignored) {}
                }
                if (contentDTO.getDurationMinutes() != null) lab.setDurationMinutes(contentDTO.getDurationMinutes());
                savedContent = labRepository.save(lab);
            } else if ("ASSIGNMENT".equals(type) && content instanceof AssignmentContent) {
                AssignmentContent ass = (AssignmentContent) content;
                if (contentDTO.getAssignmentType() != null) ass.setAssignmentType(contentDTO.getAssignmentType());
                if (contentDTO.getInstructions() != null) ass.setInstructions(contentDTO.getInstructions());
                if (contentDTO.getMaxScore() != null) ass.setMaxScore(contentDTO.getMaxScore());
                if (contentDTO.getAssignmentMetadata() != null) {
                    ass.setAssignmentMetadata(contentDTO.getAssignmentMetadata());
                    try {
                        Map<String, Object> meta = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(contentDTO.getAssignmentMetadata(), Map.class);
                        if (meta.get("gradingMode") != null) ass.setGradingMode(meta.get("gradingMode").toString());
                        if (meta.get("difficulty") != null) ass.setDifficulty(meta.get("difficulty").toString());
                        if (meta.get("passingScore") != null) ass.setPassingScore(((Number) meta.get("passingScore")).intValue());
                        if (meta.get("maxAttempts") != null) ass.setMaxAttempts(((Number) meta.get("maxAttempts")).intValue());
                        if (meta.get("dueDate") != null && !meta.get("dueDate").toString().isEmpty()) {
                            try { ass.setDueDate(LocalDateTime.parse(meta.get("dueDate").toString())); } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
                savedContent = assignmentRepository.save(ass);
            } else if (("LECTURE".equals(type) || "VIDEO".equals(type) || "IMAGE".equals(type) || "TEXT".equals(type)) && content instanceof LectureContent) {
                LectureContent lect = (LectureContent) content;
                if (contentDTO.getVideoUrl() != null) lect.setVideoUrl(contentDTO.getVideoUrl());
                if (contentDTO.getVideoWidth() != null) lect.setVideoWidth(contentDTO.getVideoWidth());
                if (contentDTO.getVideoHeight() != null) lect.setVideoHeight(contentDTO.getVideoHeight());
                if (contentDTO.getVideoFrameHtml() != null) lect.setVideoFrameHtml(contentDTO.getVideoFrameHtml());
                if (contentDTO.getImageUrl() != null) lect.setImageUrl(contentDTO.getImageUrl());
                if (contentDTO.getContentText() != null) lect.setContentText(contentDTO.getContentText());
                if (contentDTO.getContentBlocks() != null) lect.setContentBlocks(contentDTO.getContentBlocks());
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
            } else if ("LIVE".equals(type) && content instanceof LiveSessionContent) {
                LiveSessionContent live = (LiveSessionContent) content;
                if (contentDTO.getPlatform() != null) live.setPlatform(contentDTO.getPlatform());
                if (contentDTO.getMeetingUrl() != null) live.setMeetingUrl(contentDTO.getMeetingUrl());
                if (contentDTO.getMeetingId() != null) live.setMeetingId(contentDTO.getMeetingId());
                if (contentDTO.getMeetingPassword() != null) live.setMeetingPassword(contentDTO.getMeetingPassword());
                if (contentDTO.getAgenda() != null) live.setAgenda(contentDTO.getAgenda());
                if (contentDTO.getRecordSession() != null) live.setRecordSession(contentDTO.getRecordSession());
                if (contentDTO.getSessionAt() != null) {
                    try {
                        live.setSessionAt(LocalDateTime.parse(contentDTO.getSessionAt()));
                    } catch (Exception ignored) {}
                }
                if (contentDTO.getDurationMinutes() != null) live.setDurationMinutes(contentDTO.getDurationMinutes());
                savedContent = liveSessionRepository.save(live);
            } else {
                savedContent = contentRepository.save(content);
            }

            return ResponseEntity.ok(Map.of("success", true, "content", toContentResponse(savedContent)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Get course with all modules and content
    @GetMapping("/courses/{id}/full")
    @Transactional
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
                "course", toCourseResponse(course.get()),
                "modules", modules.stream().map(this::toModuleResponse).collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    // Get teacher permissions
    @GetMapping("/teacher-permissions/{teacherId}")
    public ResponseEntity<?> getTeacherPermissions(@PathVariable String teacherId,
            @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Map<String, Object> permissions = userServiceClient.getTeacherPermission(teacherId);
            return ResponseEntity.ok(Map.of("success", true, "permissions", permissions));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("success", true, "permissions", Map.of()));
        }
    }

    // Update teacher permissions
    @PutMapping("/teacher-permissions/{teacherId}")
    public ResponseEntity<?> updateTeacherPermissions(@PathVariable String teacherId,
            @RequestBody Map<String, Object> permissions,
            @RequestHeader("X-User-Id") String adminId,
            @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        permissions.put("teacherId", teacherId);
        permissions.put("grantedBy", adminId);
        permissions.put("updatedAt", LocalDateTime.now().toString());

        try {
            Map<String, Object> saved = userServiceClient.updateTeacherPermission(teacherId, permissions);
            return ResponseEntity.ok(Map.of("success", true, "permissions", saved));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update teacher permissions"));
        }
    }

    // Delete module — teachers can delete modules in courses they are assigned to
    @DeleteMapping("/modules/{id}")
    public ResponseEntity<?> deleteModule(@PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        return moduleRepository.findById(id).map(module -> {
            if (!"admin".equalsIgnoreCase(userRole)) {
                // Teachers may only delete modules in courses they are assigned to
                Long courseId = module.getCourse() != null ? module.getCourse().getId() :
                        (module.getParentModule() != null && module.getParentModule().getCourse() != null
                                ? module.getParentModule().getCourse().getId() : null);
                if (courseId == null || !courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", NOT_ASSIGNED_MSG));
                }
                try {
                    Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
                    if (!Boolean.TRUE.equals(perm.get("canEditModules"))) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "You don't have permission to delete modules"));
                    }
                } catch (Exception ex) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to delete modules"));
                }
            }

            moduleRepository.delete(module);
            return ResponseEntity.ok(Map.of("success", true, "message", "Module deleted successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Delete content — teachers can delete content in courses they are assigned to
    @DeleteMapping("/contents/{id}")
    public ResponseEntity<?> deleteContent(@PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        return contentRepository.findById(id).map(content -> {
            if (!"admin".equalsIgnoreCase(userRole)) {
                Long courseId = content.getModule() != null && content.getModule().getCourse() != null
                        ? content.getModule().getCourse().getId() : null;
                if (courseId == null || !courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", NOT_ASSIGNED_MSG));
                }
                try {
                    Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
                    if (!Boolean.TRUE.equals(perm.get("canEditContent"))) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "You don't have permission to delete content"));
                    }
                } catch (Exception ex) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to delete content"));
                }
            }

            contentRepository.delete(content);
            return ResponseEntity.ok(Map.of("success", true, "message", "Content deleted successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Reorder modules — accepts [{id, orderIndex}, ...]
    @PutMapping("/modules/reorder")
    @Transactional
    public ResponseEntity<?> reorderModules(
            @RequestBody List<Map<String, Object>> items,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"admin".equalsIgnoreCase(userRole) && !"teacher".equalsIgnoreCase(userRole)
                && !"dual".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }
        int updated = 0;
        for (Map<String, Object> item : items) {
            try {
                Long moduleId = ((Number) item.get("id")).longValue();
                Integer newOrder = ((Number) item.get("orderIndex")).intValue();
                moduleRepository.findById(moduleId).ifPresent(m -> {
                    m.setOrderIndex(newOrder);
                    m.setUpdatedAt(LocalDateTime.now());
                    moduleRepository.save(m);
                });
                updated++;
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of("success", true, "updated", updated));
    }

    // Reorder contents — accepts [{id, orderIndex}, ...]
    @PutMapping("/contents/reorder")
    @Transactional
    public ResponseEntity<?> reorderContents(
            @RequestBody List<Map<String, Object>> items,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"admin".equalsIgnoreCase(userRole) && !"teacher".equalsIgnoreCase(userRole)
                && !"dual".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }
        int updated = 0;
        for (Map<String, Object> item : items) {
            try {
                Long contentId = ((Number) item.get("id")).longValue();
                Integer newOrder = ((Number) item.get("orderIndex")).intValue();
                contentRepository.findById(contentId).ifPresent(c -> {
                    c.setOrderIndex(newOrder);
                    c.setUpdatedAt(LocalDateTime.now());
                    contentRepository.save(c);
                });
                updated++;
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of("success", true, "updated", updated));
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
            return ResponseEntity.ok(Map.of("success", true, "moduleTitle", module.getTitle(), "contents",
                    contents.stream().map(this::toContentResponse).collect(Collectors.toList())));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Sub-chapter endpoints ──────────────────────────────────────────────────

    // GET /courses/{courseId}/modules — top-level chapters + their sub-modules
    @GetMapping("/courses/{courseId}/modules")
    @Transactional
    public ResponseEntity<?> getCourseModules(@PathVariable Long courseId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"admin".equalsIgnoreCase(userRole) && !"teacher".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!courseRepository.existsById(courseId)) {
            return ResponseEntity.notFound().build();
        }

        List<CourseModule> topLevel = moduleRepository.findTopLevelByCourseId(courseId);
        List<Map<String, Object>> result = topLevel.stream().map(m -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("title", m.getTitle());
            map.put("description", m.getDescription());
            map.put("imageUrl", m.getImageUrl());
            map.put("orderIndex", m.getOrderIndex());
            map.put("isActive", m.getIsActive());
            map.put("createdBy", m.getCreatedBy());
            map.put("subModules", moduleRepository.findByParentModuleId(m.getId()).stream().map(sub -> {
                Map<String, Object> s = new java.util.LinkedHashMap<>();
                s.put("id", sub.getId());
                s.put("title", sub.getTitle());
                s.put("description", sub.getDescription());
                s.put("imageUrl", sub.getImageUrl());
                s.put("orderIndex", sub.getOrderIndex());
                s.put("isActive", sub.getIsActive());
                return s;
            }).collect(Collectors.toList()));
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("success", true, "courseId", courseId, "modules", result));
    }

    // GET /modules/{moduleId}/submodules — list sub-chapters of a chapter
    @GetMapping("/modules/{moduleId}/submodules")
    public ResponseEntity<?> getSubModules(@PathVariable Long moduleId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"admin".equalsIgnoreCase(userRole) && !"teacher".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return moduleRepository.findById(moduleId).map(parent -> {
            List<CourseModule> subs = moduleRepository.findByParentModuleId(moduleId);
            return ResponseEntity.ok(Map.of("success", true, "parentModuleId", moduleId,
                    "parentTitle", parent.getTitle(), "subModules", subs));
        }).orElse(ResponseEntity.notFound().build());
    }

    // POST /modules/{parentModuleId}/submodules — create a sub-chapter
    @PostMapping("/modules/{parentModuleId}/submodules")
    public ResponseEntity<?> createSubModule(@PathVariable Long parentModuleId,
            @RequestBody ModuleCreateDTO moduleDTO,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        return moduleRepository.findById(parentModuleId).map(parent -> {
            if (!"admin".equalsIgnoreCase(userRole)) {
                Long courseId = parent.getCourse().getId();
                if (!courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", NOT_ASSIGNED_MSG));
                }
                try {
                    Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
                    if (!Boolean.TRUE.equals(perm.get("canAddModules"))) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "You don't have permission to add sub-chapters"));
                    }
                } catch (Exception ex) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "You don't have permission to add sub-chapters"));
                }
            }

            CourseModule sub = new CourseModule();
            sub.setTitle(moduleDTO.getTitle());
            sub.setDescription(moduleDTO.getDescription());
            sub.setImageUrl(moduleDTO.getImageUrl());
            sub.setCourse(parent.getCourse());
            sub.setParentModule(parent);
            sub.setCreatedBy(userId);
            sub.setCreatedAt(LocalDateTime.now());
            sub.setUpdatedAt(LocalDateTime.now());

            if (moduleDTO.getOrderIndex() != null && moduleDTO.getOrderIndex() > 0) {
                sub.setOrderIndex(moduleDTO.getOrderIndex());
            } else {
                Integer maxOrder = moduleRepository.findMaxOrderIndexByParentId(parentModuleId);
                sub.setOrderIndex(maxOrder != null ? maxOrder + 1 : 1);
            }

            CourseModule saved = moduleRepository.save(sub);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("success", true, "subModule", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────────────────────────────────────

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

        List<Map<String, Object>> enrollments;
        try {
            enrollments = enrollmentServiceClient.getEnrollments(null, id);
        } catch (Exception e) {
            enrollments = java.util.Collections.emptyList();
        }
        
        List<Map<String, Object>> students = enrollments.stream().map(enrollment -> {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("studentId", enrollment.get("studentId"));
            data.put("progress", enrollment.get("progress"));
            data.put("enrolledAt", enrollment.get("enrolledAt"));
            data.put("completedAt", enrollment.get("completedAt"));
            data.put("status", enrollment.get("completedAt") != null ? "COMPLETED" : "IN_PROGRESS");

            // Fetch profile from user-service via Feign
            try {
                String studentId2 = (String) enrollment.get("studentId");
                Map<String, Object> profile = userServiceClient.getUserProfile(studentId2);
                if (profile != null) {
                    data.put("fullName", profile.get("fullName"));
                    data.put("email", profile.get("email"));
                }
            } catch (Exception ignored) {
            }

            return data;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "courseId", id,
            "studentCount", students.size(),
            "students", students
        ));
    }

    // ── Teacher Permission Proxy Endpoints ──────────────────────────────────────────

    /**
     * GET /api/course-management/permissions/{teacherId}
     * Proxies to user-service GET /api/users/{userId}/teacher-permission.
     * Provides a consistent /api/course-management/* namespace for the frontend.
     */
    @GetMapping("/permissions/{teacherId}")
    public ResponseEntity<?> getTeacherPermissionsProxy(@PathVariable String teacherId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        if (!"admin".equalsIgnoreCase(userRole) && !"teacher".equalsIgnoreCase(userRole)
                && !"dual".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }
        try {
            Map<String, Object> permissions = userServiceClient.getTeacherPermission(teacherId);
            return ResponseEntity.ok(permissions != null ? permissions : Map.of());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Teacher permissions not found", "teacherId", teacherId));
        }
    }

    /**
     * PUT /api/course-management/permissions/{teacherId}
     * Proxies to user-service PUT /api/users/{teacherId}/teacher-permission.
     */
    @PutMapping("/permissions/{teacherId}")
    public ResponseEntity<?> updateTeacherPermissions(@PathVariable String teacherId,
            @RequestBody Map<String, Object> permissions,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only administrators can update teacher permissions"));
        }
        try {
            Map<String, Object> updated = userServiceClient.updateTeacherPermission(teacherId, permissions);
            return ResponseEntity.ok(updated != null ? updated : Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update teacher permissions: " + e.getMessage()));
        }
    }

    // ── Private helpers for createContent ────────────────────────────────────

    private ResponseEntity<Map<String, Object>> checkTeacherContentPermission(
            CourseModule module, Long moduleId, String userId, ContentCreateDTO contentDTO) {
        Long courseId = module.getCourse().getId();
        if (!courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", NOT_ASSIGNED_MSG));
        }
        try {
            Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
            if (!Boolean.TRUE.equals(perm.get("canAddContent"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You don't have permission to add content"));
            }
            String type = contentDTO.getContentType();
            if (("QUIZ".equalsIgnoreCase(type) || "EXAM".equalsIgnoreCase(type))
                    && !Boolean.TRUE.equals(perm.get("canManageExams"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You don't have permission to manage exams/quizzes"));
            }
            Object maxCont = perm.get("maxContentPerModule");
            if (maxCont != null) {
                Long contentCount = contentRepository.countByModuleId(moduleId);
                if (contentCount >= ((Number) maxCont).longValue()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Maximum content limit reached for this module"));
                }
            }
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to add content"));
        }
        return null;
    }

    private ModuleContent buildTypedContent(ContentCreateDTO contentDTO) {
        String type = contentDTO.getContentType();
        if ("LAB".equals(type)) {
            LabContent lab = new LabContent();
            lab.setLabType(contentDTO.getLabType());
            lab.setInstructions(contentDTO.getInstructions());
            lab.setEnvironmentConfig(contentDTO.getEnvironmentConfig());
            lab.setDurationMinutes(contentDTO.getDurationMinutes());
            if (contentDTO.getEnvironmentConfig() != null) {
                try {
                    Map<String, Object> config = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(contentDTO.getEnvironmentConfig(), Map.class);
                    if (config.get("difficulty") != null) lab.setDifficultyLevel(config.get("difficulty").toString());
                    if (config.get("prerequisites") != null) lab.setPrerequisites(config.get("prerequisites").toString());
                    if (config.get("objectives") != null) lab.setLearningObjectives(config.get("objectives").toString());
                } catch (Exception ignored) {}
            }
            return lab;
        } else if ("ASSIGNMENT".equals(type)) {
            AssignmentContent ass = new AssignmentContent();
            ass.setAssignmentType(contentDTO.getAssignmentType());
            ass.setInstructions(contentDTO.getInstructions());
            ass.setMaxScore(contentDTO.getMaxScore());
            if (contentDTO.getAssignmentMetadata() != null) {
                ass.setAssignmentMetadata(contentDTO.getAssignmentMetadata());
                try {
                    Map<String, Object> meta = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(contentDTO.getAssignmentMetadata(), Map.class);
                    if (meta.get("gradingMode") != null) ass.setGradingMode(meta.get("gradingMode").toString());
                    if (meta.get("difficulty") != null) ass.setDifficulty(meta.get("difficulty").toString());
                    if (meta.get("passingScore") != null) ass.setPassingScore(((Number) meta.get("passingScore")).intValue());
                    if (meta.get("maxAttempts") != null) ass.setMaxAttempts(((Number) meta.get("maxAttempts")).intValue());
                    if (meta.get("dueDate") != null && !meta.get("dueDate").toString().isEmpty()) {
                        try { ass.setDueDate(LocalDateTime.parse(meta.get("dueDate").toString())); } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
            return ass;
        } else if ("LECTURE".equals(type) || "VIDEO".equals(type)) {
            LectureContent lect = new LectureContent();
            lect.setVideoUrl(contentDTO.getVideoUrl());
            lect.setVideoWidth(contentDTO.getVideoWidth());
            lect.setVideoHeight(contentDTO.getVideoHeight());
            lect.setVideoFrameHtml(contentDTO.getVideoFrameHtml());
            lect.setContentText(contentDTO.getContentText());
            lect.setIsPreview(contentDTO.getIsPreview() != null ? contentDTO.getIsPreview() : false);
            lect.setAttachmentUrl(contentDTO.getAttachmentUrl());
            lect.setDurationMinutes(contentDTO.getDurationMinutes());
            return lect;
        } else if ("IMAGE".equals(type)) {
            LectureContent img = new LectureContent();
            img.setImageUrl(contentDTO.getImageUrl());
            img.setContentText(contentDTO.getCaption() != null ? contentDTO.getCaption() : contentDTO.getContentText());
            return img;
        } else if ("TEXT".equals(type)) {
            LectureContent txt = new LectureContent();
            txt.setContentText(contentDTO.getContentText());
            txt.setContentBlocks(contentDTO.getContentBlocks());
            return txt;
        } else if ("ARTICLE".equals(type)) {
            LectureContent article = new LectureContent();
            article.setContentText(contentDTO.getContentText());
            article.setContentBlocks(contentDTO.getContentBlocks());
            article.setAttachmentUrl(contentDTO.getAttachmentUrl());
            return article;
        } else if ("QUIZ".equals(type) || "EXAM".equals(type)) {
            QuizContent quiz = new QuizContent();
            quiz.setQuizId(contentDTO.getQuizId());
            quiz.setTimeLimitMinutes(contentDTO.getTimeLimitMinutes());
            quiz.setPassingScore(contentDTO.getPassingScore());
            quiz.setMaxAttempts(contentDTO.getMaxAttempts());
            return quiz;
        } else if ("LIVE".equals(type)) {
            LiveSessionContent live = new LiveSessionContent();
            live.setPlatform(contentDTO.getPlatform());
            live.setMeetingUrl(contentDTO.getMeetingUrl());
            live.setMeetingId(contentDTO.getMeetingId());
            live.setMeetingPassword(contentDTO.getMeetingPassword());
            live.setAgenda(contentDTO.getAgenda());
            live.setRecordSession(contentDTO.getRecordSession() != null ? contentDTO.getRecordSession() : true);
            if (contentDTO.getSessionAt() != null) {
                try {
                    live.setSessionAt(LocalDateTime.parse(contentDTO.getSessionAt()));
                } catch (Exception ignored) {}
            }
            live.setDurationMinutes(contentDTO.getDurationMinutes());
            return live;
        }
        return null;
    }

    private ModuleContent persistContent(ModuleContent content) {
        if (content instanceof LabContent) {
            return labRepository.save((LabContent) content);
        } else if (content instanceof AssignmentContent) {
            return assignmentRepository.save((AssignmentContent) content);
        } else if (content instanceof LectureContent) {
            return lectureRepository.save((LectureContent) content);
        } else if (content instanceof QuizContent) {
            return quizRepository.save((QuizContent) content);
        } else if (content instanceof LiveSessionContent) {
            return liveSessionRepository.save((LiveSessionContent) content);
        }
        return contentRepository.save(content);
    }

    private Map<String, Object> toCourseResponse(Course course) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", course.getId());
        response.put("title", course.getTitle());
        response.put("description", course.getDescription());
        response.put("category", course.getCategory());
        response.put("difficultyLevel", course.getDifficultyLevel());
        response.put("duration", course.getDuration());
        response.put("contentUrl", course.getContentUrl());
        response.put("thumbnailUrl", course.getThumbnailUrl());
        response.put("basePrice", course.getBasePrice());
        response.put("gstPercent", course.getGstPercent());
        response.put("finalPrice", course.getFinalPrice());
        response.put("isActive", course.getActive());
        response.put("createdBy", course.getCreatedBy());
        response.put("createdAt", course.getCreatedAt());
        response.put("updatedAt", course.getUpdatedAt());
        response.put("status", course.getStatus());
        response.put("deletedAt", course.getDeletedAt());
        return response;
    }

    private Map<String, Object> toModuleResponse(CourseModule module) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", module.getId());
        response.put("title", module.getTitle());
        response.put("description", module.getDescription());
        response.put("imageUrl", module.getImageUrl());
        response.put("orderIndex", module.getOrderIndex());
        response.put("isActive", module.getActive());
        response.put("createdBy", module.getCreatedBy());
        response.put("createdAt", module.getCreatedAt());
        response.put("updatedAt", module.getUpdatedAt());
        if (module.getCourseId() != null) {
            response.put("courseId", module.getCourseId());
        }
        if (module.getParentModuleId() != null) {
            response.put("parentModuleId", module.getParentModuleId());
        }
        response.put("contents", contentRepository.findByModuleIdOrderByOrderIndex(module.getId())
                .stream().map(this::toContentResponse).collect(Collectors.toList()));
        response.put("subModules", moduleRepository.findByParentModuleId(module.getId())
                .stream().map(this::toModuleResponse).collect(Collectors.toList()));
        return response;
    }

    private Map<String, Object> toContentResponse(ModuleContent content) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", content.getId());
        response.put("title", content.getTitle());
        response.put("description", content.getDescription());
        response.put("contentType", content.getContentType());
        response.put("orderIndex", content.getOrderIndex());
        response.put("isActive", content.getActive());
        response.put("createdBy", content.getCreatedBy());
        response.put("createdAt", content.getCreatedAt());
        response.put("updatedAt", content.getUpdatedAt());
        response.put("status", content.getStatus());
        if (content.getModuleId() != null) {
            response.put("moduleId", content.getModuleId());
        }
        
        response.put("scheduledAt", content.getScheduledAt() != null ? content.getScheduledAt().toString() : null);
        response.put("isFreePreview", content.getIsFreePreview());
        response.put("requiresEnrollment", content.getRequiresEnrollment());
        response.put("visibility", content.getVisibility());
        response.put("availableFrom", content.getAvailableFrom() != null ? content.getAvailableFrom().toString() : null);
        response.put("availableUntil", content.getAvailableUntil() != null ? content.getAvailableUntil().toString() : null);
        response.put("drip", content.getDrip());
        response.put("dripDays", content.getDripDays());
        response.put("mandatory", content.getMandatory());
        response.put("completionType", content.getCompletionType());
        response.put("watchPercent", content.getWatchPercent());
        response.put("passPercent", content.getPassPercent());
        response.put("slug", content.getSlug());
        response.put("metaDesc", content.getMetaDesc());
        response.put("tags", content.getTags());
        response.put("estimatedMinutes", content.getEstimatedMinutes());
        response.put("hasPrerequisite", content.getHasPrerequisite());
        response.put("prerequisiteId", content.getPrerequisiteId());

        if (content instanceof LectureContent lecture) {
            response.put("videoUrl", lecture.getVideoUrl());
            response.put("videoWidth", lecture.getVideoWidth());
            response.put("videoHeight", lecture.getVideoHeight());
            response.put("videoFrameHtml", lecture.getVideoFrameHtml());
            response.put("imageUrl", lecture.getImageUrl());
            response.put("contentText", lecture.getContentText());
            response.put("contentBlocks", lecture.getContentBlocks());
            response.put("durationMinutes", lecture.getDurationMinutes());
            response.put("isPreview", lecture.getIsPreview());
            response.put("attachmentUrl", lecture.getAttachmentUrl());
            response.put("interactiveUrl", lecture.getInteractiveUrl());
        } else if (content instanceof LabContent lab) {
            response.put("labType", lab.getLabType());
            response.put("instructions", lab.getInstructions());
            response.put("environmentConfig", lab.getEnvironmentConfig());
            response.put("durationMinutes", lab.getDurationMinutes());
            response.put("difficultyLevel", lab.getDifficultyLevel());
            response.put("prerequisites", lab.getPrerequisites());
            response.put("learningObjectives", lab.getLearningObjectives());
            response.put("hasSolution", lab.getHasSolution());
            response.put("solutionGuide", lab.getSolutionGuide());
        } else if (content instanceof AssignmentContent assignment) {
            response.put("assignmentType", assignment.getAssignmentType());
            response.put("instructions", assignment.getInstructions());
            response.put("requirements", assignment.getRequirements());
            response.put("submissionFormat", assignment.getSubmissionFormat());
            response.put("maxScore", assignment.getMaxScore());
            response.put("dueDate", assignment.getDueDate());
            response.put("lateSubmissionAllowed", assignment.getLateSubmissionAllowed());
            response.put("latePenaltyPercent", assignment.getLatePenaltyPercent());
            response.put("rubric", assignment.getRubric());
            response.put("autoGrade", assignment.getAutoGrade());
            response.put("plagiarismCheck", assignment.getPlagiarismCheck());
            response.put("assignmentMetadata", assignment.getAssignmentMetadata());
            response.put("gradingMode", assignment.getGradingMode());
            response.put("passingScore", assignment.getPassingScore());
            response.put("difficulty", assignment.getDifficulty());
            response.put("durationMinutes", assignment.getDurationMinutes());
            response.put("maxAttempts", assignment.getMaxAttempts());
        } else if (content instanceof QuizContent quiz) {
            response.put("quizId", quiz.getQuizId());
            response.put("timeLimitMinutes", quiz.getTimeLimitMinutes());
            response.put("passingScore", quiz.getPassingScore());
            response.put("maxAttempts", quiz.getMaxAttempts());
        } else if (content instanceof LiveSessionContent live) {
            response.put("platform", live.getPlatform());
            response.put("meetingUrl", live.getMeetingUrl());
            response.put("meetingId", live.getMeetingId());
            response.put("meetingPassword", live.getMeetingPassword());
            response.put("agenda", live.getAgenda());
            response.put("recordSession", live.getRecordSession());
            response.put("sessionAt", live.getSessionAt());
            response.put("durationMinutes", live.getDurationMinutes());
        }

        return response;
    }

    private void copySettingsFields(ContentCreateDTO dto, ModuleContent content) {
        if (dto.getStatus() != null) content.setStatus(dto.getStatus());
        if (dto.getIsFreePreview() != null) content.setIsFreePreview(dto.getIsFreePreview());
        if (dto.getRequiresEnrollment() != null) content.setRequiresEnrollment(dto.getRequiresEnrollment());
        if (dto.getVisibility() != null) content.setVisibility(dto.getVisibility());
        if (dto.getDrip() != null) content.setDrip(dto.getDrip());
        if (dto.getDripDays() != null) content.setDripDays(dto.getDripDays());
        if (dto.getMandatory() != null) content.setMandatory(dto.getMandatory());
        if (dto.getCompletionType() != null) content.setCompletionType(dto.getCompletionType());
        if (dto.getWatchPercent() != null) content.setWatchPercent(dto.getWatchPercent());
        if (dto.getPassPercent() != null) content.setPassPercent(dto.getPassPercent());
        if (dto.getSlug() != null) content.setSlug(dto.getSlug());
        if (dto.getMetaDesc() != null) content.setMetaDesc(dto.getMetaDesc());
        if (dto.getTags() != null) content.setTags(dto.getTags());
        if (dto.getEstimatedMinutes() != null) content.setEstimatedMinutes(dto.getEstimatedMinutes());
        if (dto.getHasPrerequisite() != null) content.setHasPrerequisite(dto.getHasPrerequisite());
        if (dto.getPrerequisiteId() != null) content.setPrerequisiteId(dto.getPrerequisiteId());

        if (dto.getScheduledAt() != null) {
            if (dto.getScheduledAt().trim().isEmpty()) {
                content.setScheduledAt(null);
            } else {
                try { content.setScheduledAt(LocalDateTime.parse(dto.getScheduledAt())); } catch (Exception ignored) {}
            }
        }
        if (dto.getAvailableFrom() != null) {
            if (dto.getAvailableFrom().trim().isEmpty()) {
                content.setAvailableFrom(null);
            } else {
                try { content.setAvailableFrom(LocalDateTime.parse(dto.getAvailableFrom())); } catch (Exception ignored) {}
            }
        }
        if (dto.getAvailableUntil() != null) {
            if (dto.getAvailableUntil().trim().isEmpty()) {
                content.setAvailableUntil(null);
            } else {
                try { content.setAvailableUntil(LocalDateTime.parse(dto.getAvailableUntil())); } catch (Exception ignored) {}
            }
        }
    }
}
