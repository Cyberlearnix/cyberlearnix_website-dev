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
import com.cyberlearnix.shared.repository.course.*;
import org.springframework.transaction.annotation.Transactional;
import com.cyberlearnix.course.client.EnrollmentServiceClient;
import com.cyberlearnix.course.client.UserServiceClient;
import com.cyberlearnix.course.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private static final String KEY_SUCCESS = "success";
    private static final String KEY_DIFFICULTY_LEVEL = "difficultyLevel";

// ... existing autowired fields ...
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseTeacherRepository courseTeacherRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private ModuleContentRepository contentRepository;

    @Autowired
    private EnrollmentServiceClient enrollmentServiceClient;

    @Autowired
    private UserServiceClient userServiceClient;

    @GetMapping
    public ResponseEntity<?> getCourses(@RequestParam(required = false) Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean isActive,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "") String userId,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "") String userRole) {

        if (id != null) {
            return courseRepository.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100));

        List<Course> courses = new ArrayList<>();
        long totalElements = 0;
        String normalizedRole = userRole != null ? userRole.toLowerCase() : "";

        if ("student".equals(normalizedRole)) {
            // Students can only ever see active courses — ignore isActive=false if passed
            org.springframework.data.domain.Page<Course> result =
                    courseRepository.findByIsActive(true, pageable);
            courses = result.getContent();
            totalElements = result.getTotalElements();
        } else if ("admin".equals(normalizedRole) || "administrator".equals(normalizedRole)) {
            // Admin can filter by active/inactive or see all
            org.springframework.data.domain.Page<Course> result = (isActive != null)
                    ? courseRepository.findByIsActive(isActive, pageable)
                    : courseRepository.findAll(pageable);
            courses = result.getContent();
            totalElements = result.getTotalElements();
        } else if ("teacher".equals(normalizedRole) || "dual".equals(normalizedRole)) {
            // Collect all course IDs this teacher can see
            List<Long> ownIds = courseRepository.findByCreatedBy(userId).stream()
                    .map(Course::getId).collect(Collectors.toList());
            List<Long> assignedIds = courseTeacherRepository.findByTeacherId(userId).stream()
                    .map(CourseTeacher::getCourseId).collect(Collectors.toList());
            List<Long> allIds = new ArrayList<>();
            allIds.addAll(ownIds);
            allIds.addAll(assignedIds);
            if ("dual".equals(normalizedRole)) {
                try {
                    List<Long> enrolledIds = enrollmentServiceClient.getEnrollments(userId, null).stream()
                            .map(e -> ((Number) e.get("courseId")).longValue())
                            .collect(Collectors.toList());
                    allIds.addAll(enrolledIds);
                } catch (Exception ignored) {
                }
            }
            allIds = allIds.stream().distinct().collect(Collectors.toList());
            if (!allIds.isEmpty()) {
                org.springframework.data.domain.Page<Course> result = (isActive != null)
                        ? courseRepository.findByIdInAndIsActive(allIds, isActive, pageable)
                        : courseRepository.findByIdIn(allIds, pageable);
                courses = result.getContent();
                totalElements = result.getTotalElements();
            }
        }

        // Enrich with moduleCount using a single batch query
        List<Long> courseIds = courses.stream().map(Course::getId).collect(Collectors.toList());
        Map<Long, Long> moduleCountMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            moduleRepository.countByCourseIdIn(courseIds)
                    .forEach(row -> moduleCountMap.put((Long) row[0], (Long) row[1]));
        }
        List<Map<String, Object>> enrichedCourses = courses.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("title", c.getTitle());
            map.put("description", c.getDescription());
            map.put("category", c.getCategory());
            map.put(KEY_DIFFICULTY_LEVEL, c.getDifficultyLevel());
            map.put("duration", c.getDuration());
            map.put("contentUrl", c.getContentUrl());
            map.put("thumbnailUrl", c.getThumbnailUrl());
            map.put("basePrice", c.getBasePrice());
            map.put("gstPercent", c.getGstPercent());
            map.put("finalPrice", c.getFinalPrice());
            map.put("isActive", c.getActive());
            map.put("createdBy", c.getCreatedBy());
            map.put("createdAt", c.getCreatedAt());
            map.put("updatedAt", c.getUpdatedAt());
            map.put("status", c.getStatus());
            map.put("moduleCount", moduleCountMap.getOrDefault(c.getId(), 0L));
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put(KEY_SUCCESS, true);
        response.put("courses", enrichedCourses);
        response.put("pagination", Map.of(
                "page", page,
                "size", size,
                "totalElements", totalElements,
                "totalPages", (int) Math.ceil((double) totalElements / size)
        ));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createCourse(@Valid @RequestBody CourseCreateDTO courseDTO,
            @RequestHeader(value = "X-User-Id", required = true) String userId,
            @RequestHeader(value = "X-User-Role", required = true) String userRole) {

        if ("student".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Students cannot create courses"));
        }

        if ("teacher".equals(userRole) || "dual".equals(userRole)) {
            try {
                Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
                if (perm == null || !Boolean.TRUE.equals(perm.get("canCreateCourses"))) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "No permission to create courses"));
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No permission to create courses"));
            }
        }

        Course course = new Course();
        course.setTitle(courseDTO.getTitle());
        course.setDescription(courseDTO.getDescription());
        course.setContentUrl(courseDTO.getContentUrl());
        course.setThumbnailUrl(courseDTO.getThumbnailUrl());
        course.setBasePrice(courseDTO.getBasePrice());
        course.setGstPercent(courseDTO.getGstPercent());
        course.setFinalPrice(courseDTO.getFinalPrice());
        course.setCategory(courseDTO.getCategory());
        course.setDifficultyLevel(courseDTO.getDifficultyLevel());
        course.setDuration(courseDTO.getDuration());
        course.setActive(courseDTO.getIsActive() != null ? courseDTO.getIsActive() : true);
        
        course.setCreatedBy(userId);
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());

        Course saved = courseRepository.save(course);
        
        // AUTO-ASSIGN creator as teacher so they can edit it
        CourseTeacher ct = new CourseTeacher();
        ct.setCourseId(saved.getId());
        ct.setTeacherId(userId);
        courseTeacherRepository.save(ct);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(KEY_SUCCESS, true, "course", saved));
    }

    // PUT — full replace, all required fields must be provided
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> replaceCourse(@PathVariable Long id, @Valid @RequestBody CourseUpdateDTO courseDTO,
            @RequestHeader("X-User-Id") String userId, @RequestHeader("X-User-Role") String userRole) {
        return courseRepository.findById(id).map(course -> {
            boolean isAdmin = "admin".equals(userRole);
            boolean isAssignedTeacher = courseTeacherRepository.existsByCourseIdAndTeacherId(id, userId);
            if (!isAdmin && !isAssignedTeacher) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.<String, Object>of(KEY_SUCCESS, false, "error", "No permission to update this course"));
            }

            // Validate all required fields are present for a full replace
            List<String> missing = new ArrayList<>();
            if (courseDTO.getTitle() == null || courseDTO.getTitle().isBlank()) missing.add("title");
            if (courseDTO.getDescription() == null || courseDTO.getDescription().isBlank()) missing.add("description");
            if (courseDTO.getCategory() == null || courseDTO.getCategory().isBlank()) missing.add("category");
            if (courseDTO.getDifficultyLevel() == null || courseDTO.getDifficultyLevel().isBlank()) missing.add(KEY_DIFFICULTY_LEVEL);
            if (courseDTO.getDuration() == null || courseDTO.getDuration().isBlank()) missing.add("duration");
            if (courseDTO.getBasePrice() == null) missing.add("basePrice");
            if (courseDTO.getGstPercent() == null) missing.add("gstPercent");
            if (courseDTO.getFinalPrice() == null) missing.add("finalPrice");
            if (courseDTO.getIsActive() == null) missing.add("isActive");
            if (!missing.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(KEY_SUCCESS, false, "error", "Missing required fields for full update", "fields", missing));
            }

            // Replace all fields
            course.setTitle(courseDTO.getTitle());
            course.setDescription(courseDTO.getDescription());
            course.setCategory(courseDTO.getCategory());
            course.setDifficultyLevel(courseDTO.getDifficultyLevel());
            course.setDuration(courseDTO.getDuration());
            course.setContentUrl(courseDTO.getContentUrl());
            course.setThumbnailUrl(courseDTO.getThumbnailUrl());
            course.setBasePrice(courseDTO.getBasePrice());
            course.setGstPercent(courseDTO.getGstPercent());
            course.setFinalPrice(courseDTO.getFinalPrice());
            course.setActive(courseDTO.getIsActive());
            course.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(Map.of(KEY_SUCCESS, true, "course", courseRepository.save(course)));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.<String, Object>of(KEY_SUCCESS, false, "error", "Course not found")));
    }

    // PATCH — partial update, only provided fields are changed
    @org.springframework.web.bind.annotation.PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patchCourse(@PathVariable Long id, @RequestBody CourseUpdateDTO courseDTO,
            @RequestHeader("X-User-Id") String userId, @RequestHeader("X-User-Role") String userRole) {
        return courseRepository.findById(id).map(course -> {
            boolean isAdmin = "admin".equals(userRole);
            boolean isAssignedTeacher = courseTeacherRepository.existsByCourseIdAndTeacherId(id, userId);
            if (!isAdmin && !isAssignedTeacher) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.<String, Object>of(KEY_SUCCESS, false, "error", "No permission to update this course"));
            }

            // Apply only non-null fields
            if (courseDTO.getTitle() != null) course.setTitle(courseDTO.getTitle());
            if (courseDTO.getDescription() != null) course.setDescription(courseDTO.getDescription());
            if (courseDTO.getCategory() != null) course.setCategory(courseDTO.getCategory());
            if (courseDTO.getDifficultyLevel() != null) course.setDifficultyLevel(courseDTO.getDifficultyLevel());
            if (courseDTO.getDuration() != null) course.setDuration(courseDTO.getDuration());
            if (courseDTO.getContentUrl() != null) course.setContentUrl(courseDTO.getContentUrl());
            if (courseDTO.getThumbnailUrl() != null) course.setThumbnailUrl(courseDTO.getThumbnailUrl());
            if (courseDTO.getBasePrice() != null) course.setBasePrice(courseDTO.getBasePrice());
            if (courseDTO.getGstPercent() != null) course.setGstPercent(courseDTO.getGstPercent());
            if (courseDTO.getFinalPrice() != null) course.setFinalPrice(courseDTO.getFinalPrice());
            if (courseDTO.getIsActive() != null) course.setActive(courseDTO.getIsActive());
            course.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(Map.of(KEY_SUCCESS, true, "course", courseRepository.save(course)));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.<String, Object>of(KEY_SUCCESS, false, "error", "Course not found")));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteCourse(@PathVariable Long id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(KEY_SUCCESS, false, "error", "Authentication required"));
        }
        String userId = authentication.getPrincipal() != null ? authentication.getPrincipal().toString() : "";
        String userRole = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", "").toLowerCase())
                .orElse("");
        boolean isAdmin = "admin".equals(userRole);
        boolean isAssignedTeacher = courseTeacherRepository.existsByCourseIdAndTeacherId(id, userId);
        if (!isAdmin && !isAssignedTeacher) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(KEY_SUCCESS, false, "error", "No permission to delete this course"));
        }
        return courseRepository.findById(id).map(course -> {
            if ("TRASHED".equals(course.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(KEY_SUCCESS, false, "error", "Course is already in trash"));
            }
            course.setStatus("TRASHED");
            course.setActive(false);
            course.setDeletedAt(LocalDateTime.now());
            courseRepository.save(course);
            return ResponseEntity.ok(Map.of(
                    KEY_SUCCESS, true,
                    "message", "Course moved to trash. You can restore it later.",
                    "id", id));
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(KEY_SUCCESS, false, "error", "Course not found", "id", id)));
    }

    @GetMapping("/trash")
    public ResponseEntity<?> getTrashedCourses(Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(KEY_SUCCESS, false, "error", "Authentication required"));
        }
        String userId = authentication.getPrincipal() != null ? authentication.getPrincipal().toString() : "";
        String userRole = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", "").toLowerCase())
                .orElse("");
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by("deletedAt").descending());
        org.springframework.data.domain.Page<Course> trashed;
        if ("admin".equals(userRole)) {
            trashed = courseRepository.findByStatus("TRASHED", pageable);
        } else {
            trashed = courseRepository.findByStatusAndCreatedBy("TRASHED", userId, pageable);
        }
        List<Map<String, Object>> enriched = trashed.getContent().stream().map(c -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("title", c.getTitle());
            map.put("description", c.getDescription());
            map.put("category", c.getCategory());
            map.put(KEY_DIFFICULTY_LEVEL, c.getDifficultyLevel());
            map.put("duration", c.getDuration());
            map.put("thumbnailUrl", c.getThumbnailUrl());
            map.put("basePrice", c.getBasePrice());
            map.put("finalPrice", c.getFinalPrice());
            map.put("status", c.getStatus());
            map.put("createdBy", c.getCreatedBy());
            map.put("createdAt", c.getCreatedAt());
            map.put("deletedAt", c.getDeletedAt());
            return map;
        }).collect(Collectors.toList());
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put(KEY_SUCCESS, true);
        response.put("courses", enriched);
        response.put("pagination", Map.of(
                "page", page,
                "size", size,
                "totalElements", trashed.getTotalElements(),
                "totalPages", trashed.getTotalPages()
        ));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/restore")
    @Transactional
    public ResponseEntity<?> restoreCourse(@PathVariable Long id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(KEY_SUCCESS, false, "error", "Authentication required"));
        }
        String userId = authentication.getPrincipal() != null ? authentication.getPrincipal().toString() : "";
        String userRole = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", "").toLowerCase())
                .orElse("");
        boolean isAdmin = "admin".equals(userRole);
        boolean isAssignedTeacher = courseTeacherRepository.existsByCourseIdAndTeacherId(id, userId);
        if (!isAdmin && !isAssignedTeacher) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(KEY_SUCCESS, false, "error", "No permission to restore this course"));
        }
        return courseRepository.findById(id).map(course -> {
            if (!"TRASHED".equals(course.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(KEY_SUCCESS, false, "error", "Course is not in trash", "status", course.getStatus()));
            }
            course.setStatus("APPROVED");
            course.setActive(true);
            course.setDeletedAt(null);
            courseRepository.save(course);
            return ResponseEntity.ok(Map.of(
                    KEY_SUCCESS, true,
                    "message", "Course restored successfully",
                    "id", id));
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(KEY_SUCCESS, false, "error", "Course not found", "id", id)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCourseById(@PathVariable Long id) {
        return courseRepository.findById(id)
                .map(c -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", c.getId());
                    map.put("title", c.getTitle());
                    map.put("description", c.getDescription());
                    map.put("category", c.getCategory());
                    map.put(KEY_DIFFICULTY_LEVEL, c.getDifficultyLevel());
                    map.put("duration", c.getDuration());
                    map.put("thumbnailUrl", c.getThumbnailUrl());
                    map.put("basePrice", c.getBasePrice());
                    map.put("finalPrice", c.getFinalPrice());
                    map.put("isActive", c.getActive());
                    map.put("status", c.getStatus());
                    map.put("createdAt", c.getCreatedAt());
                    return (ResponseEntity<?>) ResponseEntity.ok(map);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Student View: Full Curriculum (Modules & Content Titles)
    @GetMapping("/{id}/curriculum")
    public ResponseEntity<?> getCourseCurriculum(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = true) String userId,
            @RequestHeader(value = "X-User-Role", required = true) String userRole) {
        // Verify student is enrolled (skip for admins and teachers assigned to the course)
        boolean isAdmin = "admin".equals(userRole);
        boolean isAssignedTeacher = courseTeacherRepository.existsByCourseIdAndTeacherId(id, userId);
        boolean isEnrolled = false;
        if (!isAdmin && !isAssignedTeacher) {
            try {
                isEnrolled = enrollmentServiceClient.isEnrolled(userId, id);
            } catch (Exception ignored) {
                // enrollment-service unavailable — deny access
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(KEY_SUCCESS, false, "error", "Unable to verify enrollment"));
            }
            if (!isEnrolled) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(KEY_SUCCESS, false, "error", "You are not enrolled in this course"));
            }
        }

        return courseRepository.findById(id).map(course -> {
            List<CourseModule> modules = moduleRepository.findByCourseIdOrderByOrderIndex(id);
            List<Map<String, Object>> moduleMaps = modules.stream().map(this::toModuleResponse).collect(Collectors.toList());
            return ResponseEntity.ok(Map.of(
                    KEY_SUCCESS, true,
                    "courseId", course.getId(),
                    "courseTitle", course.getTitle(),
                    "modules", moduleMaps));
        }).orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toModuleResponse(CourseModule module) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", module.getId());
        map.put("title", module.getTitle());
        map.put("description", module.getDescription());
        map.put("orderIndex", module.getOrderIndex());
        map.put("parentModuleId", module.getParentModule() != null ? module.getParentModule().getId() : null);
        map.put("courseId", module.getCourse() != null ? module.getCourse().getId() : null);
        map.put("createdAt", module.getCreatedAt());
        map.put("updatedAt", module.getUpdatedAt());

        // Fetch content for this module
        List<ModuleContent> contents = contentRepository.findByModuleIdOrderByOrderIndex(module.getId());
        List<Map<String, Object>> contentMaps = contents.stream().map(this::toContentResponse).collect(Collectors.toList());
        map.put("contents", contentMaps);

        return map;
    }

    private Map<String, Object> toContentResponse(ModuleContent content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", content.getId());
        map.put("title", content.getTitle());
        map.put("description", content.getDescription());
        map.put("contentType", content.getContentType());
        map.put("orderIndex", content.getOrderIndex());
        map.put("moduleId", content.getModule() != null ? content.getModule().getId() : null);
        map.put("createdAt", content.getCreatedAt());
        map.put("updatedAt", content.getUpdatedAt());

        // Add type-specific fields
        if (content instanceof LectureContent) {
            LectureContent lect = (LectureContent) content;
            map.put("videoUrl", lect.getVideoUrl());
            map.put("imageUrl", lect.getImageUrl());
            map.put("contentText", lect.getContentText());
            map.put("contentBlocks", lect.getContentBlocks());
            map.put("durationMinutes", lect.getDurationMinutes());
            map.put("isPreview", lect.getIsPreview());
            map.put("attachmentUrl", lect.getAttachmentUrl());
        } else if (content instanceof LabContent) {
            LabContent lab = (LabContent) content;
            map.put("labType", lab.getLabType());
            map.put("instructions", lab.getInstructions());
            map.put("environmentConfig", lab.getEnvironmentConfig());
            map.put("durationMinutes", lab.getDurationMinutes());
            map.put("difficultyLevel", lab.getDifficultyLevel());
            map.put("prerequisites", lab.getPrerequisites());
            map.put("solutionGuide", lab.getSolutionGuide());
        } else if (content instanceof AssignmentContent) {
            AssignmentContent ass = (AssignmentContent) content;
            map.put("assignmentType", ass.getAssignmentType());
            map.put("instructions", ass.getInstructions());
            map.put("requirements", ass.getRequirements());
            map.put("maxScore", ass.getMaxScore());
            map.put("dueDate", ass.getDueDate());
            map.put("lateSubmissionAllowed", ass.getLateSubmissionAllowed());
            map.put("latePenaltyPercent", ass.getLatePenaltyPercent());
            map.put("rubric", ass.getRubric());
            map.put("plagiarismCheck", ass.getPlagiarismCheck());
        } else if (content instanceof QuizContent) {
            QuizContent quiz = (QuizContent) content;
            map.put("quizId", quiz.getQuizId());
            map.put("timeLimitMinutes", quiz.getTimeLimitMinutes());
            map.put("passingScore", quiz.getPassingScore());
            map.put("maxAttempts", quiz.getMaxAttempts());
            // Don't include questions here to avoid lazy loading issues
        }

        return map;
    }

    /**
     * GET /api/courses/teacher/{userId}
     * Returns all courses assigned to or created by the given teacher.
     * Used by teacher dashboard to list manageable courses.
     */
    @GetMapping("/teacher/{userId}")
    public ResponseEntity<?> getCoursesByTeacher(@PathVariable String userId,
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestHeader(value = "X-User-Role", required = false) String callerRole) {
        List<Long> ownIds = courseRepository.findByCreatedBy(userId).stream()
                .map(Course::getId).collect(Collectors.toList());
        List<Long> assignedIds = courseTeacherRepository.findByTeacherId(userId).stream()
                .map(CourseTeacher::getCourseId).collect(Collectors.toList());
        List<Long> allIds = new ArrayList<>();
        allIds.addAll(ownIds);
        allIds.addAll(assignedIds);
        allIds = allIds.stream().distinct().collect(Collectors.toList());
        if (allIds.isEmpty()) {
            return ResponseEntity.ok(Map.of(KEY_SUCCESS, true, "courses", List.of()));
        }
        List<Course> courses = courseRepository.findByIdIn(allIds,
                org.springframework.data.domain.PageRequest.of(0, 500)).getContent();
        List<Map<String, Object>> result = courses.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("title", c.getTitle());
            map.put("description", c.getDescription());
            map.put("thumbnailUrl", c.getThumbnailUrl());
            map.put("category", c.getCategory());
            map.put(KEY_DIFFICULTY_LEVEL, c.getDifficultyLevel());
            map.put("isActive", c.getActive());
            map.put("status", c.getStatus());
            map.put("createdBy", c.getCreatedBy());
            map.put("createdAt", c.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true, "courses", result));
    }

    /**
     * Internal endpoint — returns course pricing data for inter-service calls.
     * No authentication required; used by enrollment-service to resolve course price.
     */
    @GetMapping("/{id}/price")
    public ResponseEntity<?> getCoursePrice(@PathVariable Long id) {
        return courseRepository.findById(id)
                .map(c -> ResponseEntity.ok(Map.of(
                        "courseId", c.getId(),
                        "title", c.getTitle() != null ? c.getTitle() : "",
                        "basePrice", c.getBasePrice() != null ? c.getBasePrice() : 0.0,
                        "gstPercent", c.getGstPercent() != null ? c.getGstPercent() : 0,
                        "finalPrice", c.getFinalPrice() != null ? c.getFinalPrice() : 0.0
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
