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
import com.cyberlearnix.lab.service.LabService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Admin / instructor assigns a lab template to a student.
     * Provisions and starts the Docker container immediately.
     */
    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<LabAssignment> assignLab(@Valid @RequestBody AssignLabRequest request,
                                                   @RequestHeader(value = "X-User-Id", required = false) String callerId) {
        String instructorId = request.getInstructorId() != null ? request.getInstructorId() : callerId;
        LabAssignment assignment = labService.assignLab(request.getStudentId(), request.getTemplateId(), instructorId);
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
     */
    @GetMapping("/my-labs/course/{courseId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LabAssignment>> getMyLabsForCourse(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") String studentId) {
        return ResponseEntity.ok(courseLabService.getMyLabsForCourse(studentId, courseId));
    }

    /**
     * Instructor/Admin: get all approval requests for a course.
     */
    @GetMapping("/courses/{courseId}/requests")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<LabApprovalRequest>> getCourseRequests(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseLabService.getCourseRequests(courseId));
    }
}
