package com.cyberlearnix.lab.controller;

import com.cyberlearnix.lab.dto.AssignLabRequest;
import com.cyberlearnix.lab.dto.CreateTemplateRequest;
import com.cyberlearnix.lab.entity.AssignmentStatus;
import com.cyberlearnix.lab.entity.LabAssignment;
import com.cyberlearnix.lab.entity.LabTemplate;
import com.cyberlearnix.lab.repository.LabAssignmentRepository;
import com.cyberlearnix.lab.repository.LabTemplateRepository;
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
                                                   @RequestHeader(value = "X-User-Id", required = false) Long callerId) {
        Long instructorId = request.getInstructorId() != null ? request.getInstructorId() : callerId;
        LabAssignment assignment = labService.assignLab(request.getStudentId(), request.getTemplateId(), instructorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    /**
     * Student retrieves their active lab details including WebSocket connection info.
     * X-User-Id is injected by the gateway JWT filter.
     */
    @GetMapping("/my-lab")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getMyLab(@RequestHeader("X-User-Id") Long studentId) {
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
}
