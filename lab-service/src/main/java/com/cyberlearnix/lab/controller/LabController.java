package com.cyberlearnix.lab.controller;

import com.cyberlearnix.lab.dto.ApprovalDecisionDto;
import com.cyberlearnix.lab.dto.AssignLabRequest;
import com.cyberlearnix.lab.dto.CourseLabConfigRequest;
import com.cyberlearnix.lab.dto.CreateTemplateRequest;
import com.cyberlearnix.lab.dto.LabRequestDto;
import com.cyberlearnix.lab.entity.AssignmentStatus;
import com.cyberlearnix.lab.entity.CourseLabConfig;
import com.cyberlearnix.lab.entity.LabApprovalRequest;
import com.cyberlearnix.lab.entity.LabAssignment;
import com.cyberlearnix.lab.entity.LabTemplate;
import com.cyberlearnix.lab.repository.LabAssignmentRepository;
import com.cyberlearnix.lab.repository.LabTemplateRepository;
import com.cyberlearnix.lab.service.CourseLabService;
import com.cyberlearnix.lab.service.DockerClientService;
import com.cyberlearnix.lab.service.LabImageBuildService;
import com.cyberlearnix.lab.service.LabService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/labs")
@RequiredArgsConstructor
public class LabController {

    private final LabService labService;
    private final CourseLabService courseLabService;
    private final LabTemplateRepository templateRepository;
    private final LabAssignmentRepository assignmentRepository;
    private final DockerClientService dockerClientService;
    private final LabImageBuildService labImageBuildService;

    /**
     * Admin / instructor assigns a lab template to a student.
     * Provisions and starts the Docker container immediately.
     */
    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<LabAssignment> assignLab(@Valid @RequestBody AssignLabRequest request,
                                                   @RequestHeader(value = "X-User-Id", required = false) String callerId) {
        String instructorId = request.getInstructorId() != null ? request.getInstructorId() : callerId;
        LabAssignment assignment = labService.assignLab(
                request.getStudentId(), request.getTemplateId(), instructorId, request.getCourseId());
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    /**
     * Student retrieves their active lab details including WebSocket connection info.
     * X-User-Id is injected by the gateway JWT filter.
     */
    @GetMapping("/my-lab")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getMyLab(@RequestHeader("X-User-Id") String studentId) {
        return labService.getStudentActiveLab(studentId)
                .map(a -> {
                    Map<String, Object> connectionInfo = Map.of(
                            "websocketPath", "/labs/terminal/" + a.getId(),
                            "containerId", a.getContainerId() != null ? a.getContainerId() : "",
                            "containerName", a.getContainerName() != null ? a.getContainerName() : "",
                            "status", a.getStatus().name()
                    );
                    Map<String, Object> response = Map.of(
                            "assignment", a,
                            "connectionInfo", connectionInfo
                    );
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Student or instructor stops a running lab (container paused, not removed).
     */
    @PostMapping("/{assignmentId}/stop")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LabAssignment> stopLab(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(labService.stopLab(assignmentId));
    }

    /**
     * Student resumes their own PAUSED lab (restarts the container).
     */
    @PostMapping("/{assignmentId}/resume")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LabAssignment> resumeLab(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(labService.resumeLab(assignmentId));
    }

    /**
     * Admin view: all currently running containers with container stats.
     */
    @GetMapping("/admin/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllActiveContainers() {
        List<LabAssignment> running = assignmentRepository.findByStatus(AssignmentStatus.RUNNING);
        List<Map<String, Object>> result = running.stream()
                .map(a -> {
                    Map<String, Object> stats = a.getContainerId() != null
                            ? dockerClientService.getContainerStats(a.getContainerId())
                            : Map.of("status", "no_container");
                    return Map.<String, Object>of(
                            "assignment", a,
                            "containerStats", stats
                    );
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * List all active lab templates (available to any authenticated user).
     */
    @GetMapping("/templates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LabTemplate>> getActiveTemplates() {
        return ResponseEntity.ok(templateRepository.findByIsActiveTrue());
    }

    /**
     * Admin creates a new lab template.
     */
    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabTemplate> createTemplate(@Valid @RequestBody CreateTemplateRequest request) {
        LabTemplate template = new LabTemplate();
        template.setName(request.getName());
        template.setDockerImage(request.getDockerImage());
        template.setCpuLimit(request.getCpuLimit());
        template.setMemoryLimit(request.getMemoryLimit());
        template.setDescription(request.getDescription());
        template.setToolsList(request.getToolsList());
        template.setIsActive(true);
        template.setCreatedAt(Instant.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(templateRepository.save(template));
    }

    // ─── Course-Linked Lab Endpoints ──────────────────────────────────────────

    /**
     * Admin: link a lab template to a course.
     */
    @PostMapping("/courses/configure")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourseLabConfig> linkTemplateToCourse(
            @Valid @RequestBody CourseLabConfigRequest req,
            @RequestHeader("X-User-Id") String adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseLabService.linkTemplateToCourse(req, adminId));
    }

    /**
     * Admin: list ALL active course-lab configurations (global view).
     */
    @GetMapping("/admin/course-configs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CourseLabConfig>> getAllCourseConfigs() {
        return ResponseEntity.ok(courseLabService.getAllCourseLabConfigs());
    }

    /**
     * Anyone authenticated: get lab templates available in a course.
     */
    @GetMapping("/courses/{courseId}/templates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CourseLabConfig>> getCourseTemplates(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseLabService.getCourseLabConfigs(courseId));
    }

    /**
     * Instructor: request a lab assignment for a student in a course.
     */
    @PostMapping("/courses/request")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<LabApprovalRequest> requestLab(
            @Valid @RequestBody LabRequestDto dto,
            @RequestHeader("X-User-Id") String instructorId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseLabService.requestLab(dto, instructorId));
    }

    /**
     * Admin: view all pending approval requests.
     */
    @GetMapping("/admin/approvals")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LabApprovalRequest>> getPendingApprovals() {
        return ResponseEntity.ok(courseLabService.getPendingApprovals());
    }

    /**
     * Admin: approve or reject a lab request.
     */
    @PostMapping("/admin/approvals/{requestId}/decide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabApprovalRequest> decide(
            @PathVariable Long requestId,
            @Valid @RequestBody ApprovalDecisionDto decision,
            @RequestHeader("X-User-Id") String adminId) {
        return ResponseEntity.ok(courseLabService.processApproval(requestId, decision, adminId));
    }

    /**
     * Student: get their lab assignments for a specific course.
     * Falls back to any active lab with no course link if no course-specific assignment found.
     */
    @GetMapping("/my-labs/course/{courseId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LabAssignment>> getMyLabsForCourse(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") String studentId) {
        return ResponseEntity.ok(courseLabService.getMyLabsForCourse(studentId, courseId));
    }

    /**
     * Student: get active lab status for a course (single object, not a list).
     * Returns the assignment + connection info if a lab is running, or a status-only
     * response if the lab is not yet started. Includes the full lab template details.
     * This endpoint is the preferred way for the student dashboard to check lab status
     * per course — it handles null-courseId fallback transparently.
     */
    @GetMapping("/my-lab/course/{courseId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getMyLabStatusForCourse(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") String studentId) {
        List<LabAssignment> labs = courseLabService.getMyLabsForCourse(studentId, courseId);
        if (labs.isEmpty()) {
            // Check if the course even has a lab configured
            List<com.cyberlearnix.lab.entity.CourseLabConfig> configs =
                    courseLabService.getCourseLabConfigs(courseId);
            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("status", "NOT_STARTED");
            resp.put("hasLabConfigured", !configs.isEmpty());
            resp.put("labConfig", configs.isEmpty() ? null : configs.get(0));
            resp.put("assignment", null);
            return ResponseEntity.ok(resp);
        }
        // Return the most relevant assignment (RUNNING > PROVISIONING > PAUSED > others)
        LabAssignment best = labs.stream()
                .sorted(java.util.Comparator.comparingInt(a -> {
                    switch (a.getStatus()) {
                        case RUNNING: return 0;
                        case PROVISIONING: return 1;
                        case PAUSED: return 2;
                        default: return 3;
                    }
                }))
                .findFirst().get();
        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("status", best.getStatus().name());
        resp.put("hasLabConfigured", true);
        resp.put("assignment", best);
        if (best.getStatus() == AssignmentStatus.RUNNING) {
            resp.put("connectionInfo", Map.of(
                    "websocketPath", "/labs/terminal/" + best.getId(),
                    "containerId", best.getContainerId() != null ? best.getContainerId() : "",
                    "containerName", best.getContainerName() != null ? best.getContainerName() : ""
            ));
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * Student: self-service start (or request) a lab for a course.
     * If requiresApproval=false on the course config, the lab is provisioned immediately.
     * If requiresApproval=true, a PENDING approval request is created.
     * Returns 200 with { requiresApproval, approvalRequest, assignment? }.
     * Returns 409 if the student already has an active lab for this course.
     */
    @PostMapping("/my-lab/course/{courseId}/start")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> startMyLabForCourse(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") String studentId) {
        try {
            Map<String, Object> result = courseLabService.startLabForCourse(courseId, studentId);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("ALREADY_ACTIVE:")) {
                long assignmentId = Long.parseLong(ex.getMessage().split(":")[1]);
                LabAssignment existing = assignmentRepository.findById(assignmentId).orElseThrow();
                Map<String, Object> resp = new java.util.LinkedHashMap<>();
                resp.put("status", existing.getStatus().name());
                resp.put("assignment", existing);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(resp);
            }
            throw ex;
        }
    }

    /**
     * Student: get all their lab assignments across all courses.
     */
    @GetMapping("/my-labs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LabAssignment>> getAllMyLabs(@RequestHeader("X-User-Id") String studentId) {
        return ResponseEntity.ok(assignmentRepository.findByStudentId(studentId));
    }

    /**
     * Student: self-request a lab for a course they are enrolled in.
     * If the CourseLabConfig has requiresApproval=false it is auto-provisioned immediately.
     */
    @PostMapping("/my-labs/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LabApprovalRequest> requestMyLab(
            @Valid @RequestBody LabRequestDto dto,
            @RequestHeader("X-User-Id") String studentId) {
        // Override studentId from the authenticated user — students cannot request on behalf of others
        dto.setStudentId(studentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(courseLabService.requestLab(dto, studentId));
    }

    /**
     * Instructor/Admin: get all approval requests for a course.
     */
    @GetMapping("/courses/{courseId}/requests")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<LabApprovalRequest>> getCourseRequests(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseLabService.getCourseRequests(courseId));
    }

    // ─── Pre-installation Build Pipeline ─────────────────────────────────────

    /**
     * Admin: save or update the setup script for a course lab.
     */
    @PutMapping("/courses/{courseId}/setup-script")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourseLabConfig> saveSetupScript(
            @PathVariable Long courseId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(labImageBuildService.saveSetupScript(courseId, body.get("setupScript")));
    }

    /**
     * Admin: trigger async build of the course lab image.
     * Returns immediately; log is streamed via /build-log-stream.
     */
    @PostMapping("/courses/{courseId}/build-image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> buildLabImage(@PathVariable Long courseId) {
        labImageBuildService.triggerBuild(courseId);
        return ResponseEntity.accepted().body(Map.of("status", "BUILDING", "message", "Build started"));
    }

    /**
     * Admin: SSE endpoint — streams live build log output.
     */
    @GetMapping(value = "/courses/{courseId}/build-log-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public SseEmitter streamBuildLog(@PathVariable Long courseId) {
        return labImageBuildService.createLogEmitter(courseId);
    }

    /**
     * Admin: get current build status + final log.
     */
    @GetMapping("/courses/{courseId}/build-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getBuildStatus(@PathVariable Long courseId) {
        return ResponseEntity.ok(labImageBuildService.getBuildStatus(courseId));
    }

    /**
     * Admin: publish the staged image — students will get it on next lab start.
     */
    @PostMapping("/courses/{courseId}/publish-image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourseLabConfig> publishStagedImage(@PathVariable Long courseId) {
        return ResponseEntity.ok(labImageBuildService.publishStagedImage(courseId));
    }
}
