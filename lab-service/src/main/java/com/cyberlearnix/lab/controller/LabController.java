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
     * X-Accel-Buffering: no tells nginx to pass events through immediately without
     * buffering, which prevents net::ERR_HTTP2_PROTOCOL_ERROR in the browser.
     */
    @GetMapping(value = "/courses/{courseId}/build-log-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public SseEmitter streamBuildLog(@PathVariable Long courseId,
                                     jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
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

    // ─── Container Dependency Verification ───────────────────────────────────

    /**
     * Admin: verify that all expected dependencies are installed and working
     * inside a running student container.
     *
     * Runs the built-in check script (scripts/check-lab-dependencies.sh) inside
     * the specified assignment's container and returns a structured JSON report:
     * {
     *   "assignmentId": 32,
     *   "studentId": "...",
     *   "summary": { "passed": 28, "failed": 2, "total": 30 },
     *   "results": [
     *     { "tool": "python3", "status": "PASS", "version": "Python 3.10.12" },
     *     { "tool": "nmap",    "status": "FAIL", "detail": "binary not found in PATH" }
     *   ]
     * }
     *
     * The container must be in RUNNING status.
     */
    @PostMapping("/admin/containers/{assignmentId}/verify-deps")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> verifyContainerDeps(@PathVariable Long assignmentId) {
        LabAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Assignment not found: " + assignmentId));

        if (assignment.getContainerId() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Assignment " + assignmentId + " has no container"));
        }
        if (assignment.getStatus() != AssignmentStatus.RUNNING) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Container is not running (status=" + assignment.getStatus() + ")",
                    "assignmentId", assignmentId,
                    "status", assignment.getStatus().name()
            ));
        }

        String checkScript = buildDepCheckScript();
        String rawOutput;
        try {
            rawOutput = dockerClientService.execScript(assignment.getContainerId(), checkScript, 90);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to exec check script: " + e.getMessage(),
                    "assignmentId", assignmentId
            ));
        }

        // Parse RESULT|STATUS|TOOL|DETAIL lines
        java.util.List<Map<String, String>> results = new java.util.ArrayList<>();
        long passed = 0, failed = 0, warned = 0;
        for (String line : rawOutput.split("\\n")) {
            if (!line.startsWith("RESULT|")) continue;
            String[] parts = line.split("\\|", 4);
            if (parts.length < 4) continue;
            String status = parts[1].trim();
            String tool   = parts[2].trim();
            String detail = parts[3].trim();
            Map<String, String> entry = new java.util.LinkedHashMap<>();
            entry.put("tool", tool);
            entry.put("status", status);
            entry.put("detail", detail);
            results.add(entry);
            if ("PASS".equals(status)) passed++;
            else if ("FAIL".equals(status)) failed++;
            else warned++;
        }

        // Parse SUMMARY line
        long total = passed + failed;
        for (String line : rawOutput.split("\\n")) {
            if (line.startsWith("SUMMARY|")) {
                String[] s = line.split("\\|");
                if (s.length >= 4) {
                    try { total = Long.parseLong(s[3].trim()); } catch (NumberFormatException ignored) {}
                }
                break;
            }
        }

        Map<String, Object> summary = Map.of(
                "passed", passed,
                "failed", failed,
                "warnings", warned,
                "total", total
        );

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("assignmentId", assignmentId);
        response.put("studentId", assignment.getStudentId());
        response.put("containerId", assignment.getContainerId());
        response.put("containerName", assignment.getContainerName());
        response.put("summary", summary);
        response.put("results", results);
        return ResponseEntity.ok(response);
    }

    /** Inline dependency check script — mirrors scripts/check-lab-dependencies.sh */
    private static String buildDepCheckScript() {
        return "#!/bin/bash\n" +
               "PASS=0; FAIL=0\n" +
               "chk() { local t=$1 c=$2; local v; v=$(eval \"$c\" 2>&1 | head -1 | tr -d '\\n');" +
               " if [ $? -eq 0 ] && [ -n \"$v\" ]; then echo \"RESULT|PASS|$t|$v\"; PASS=$((PASS+1));" +
               " else echo \"RESULT|FAIL|$t|not found or not working\"; FAIL=$((FAIL+1)); fi; }\n" +
               "chkc() { local t=$1 b=$2 v=$3; if command -v \"$b\" &>/dev/null; then" +
               " local ver; ver=$(eval \"$v\" 2>&1 | head -1 | tr -d '\\n'); echo \"RESULT|PASS|$t|$ver\"; PASS=$((PASS+1));" +
               " else echo \"RESULT|FAIL|$t|binary not found\"; FAIL=$((FAIL+1)); fi; }\n" +
               "chkpy() { local p=$1; if python3 -c \"import $p\" 2>/dev/null; then" +
               " v=$(python3 -c \"import $p; print(getattr($p,'__version__','installed'))\" 2>/dev/null || echo installed);" +
               " echo \"RESULT|PASS|python:$p|$v\"; PASS=$((PASS+1));" +
               " else echo \"RESULT|FAIL|python:$p|import failed\"; FAIL=$((FAIL+1)); fi; }\n" +
               // System
               "chkc bash bash 'bash --version'\n" +
               "chkc curl curl 'curl --version'\n" +
               "chkc wget wget 'wget --version'\n" +
               "chkc git git 'git --version'\n" +
               "chkc vim vim 'vim --version'\n" +
               "chkc nano nano 'nano --version'\n" +
               // Python
               "chkc python3 python3 'python3 --version'\n" +
               "chkc pip3 pip3 'pip3 --version'\n" +
               "chkpy requests\n" +
               "chkpy cryptography\n" +
               "chkpy flask\n" +
               "chkpy pytest\n" +
               // Node
               "chkc node node 'node --version'\n" +
               "chkc npm npm 'npm --version'\n" +
               // Java
               "chkc java java 'java -version 2>&1'\n" +
               "chkc javac javac 'javac -version 2>&1'\n" +
               // Build
               "chkc gcc gcc 'gcc --version'\n" +
               "chkc g++ g++ 'g++ --version'\n" +
               "chkc make make 'make --version'\n" +
               // Network/security
               "chkc nmap nmap 'nmap --version'\n" +
               "chkc netcat nc 'nc --version 2>&1 || echo netcat-installed'\n" +
               "chkc ping ping 'ping -V 2>&1 || echo ping-installed'\n" +
               "chkc ssh ssh 'ssh -V 2>&1'\n" +
               "chkc ip ip 'ip -V 2>&1'\n" +
               // DB clients
               "chkc sqlite3 sqlite3 'sqlite3 --version'\n" +
               "chkc psql psql 'psql --version'\n" +
               // Runtime sanity
               "if python3 -c \"import socket;s=socket.socket();s.bind(('127.0.0.1',0));s.close();print('ok')\" 2>/dev/null|grep -q ok;" +
               " then echo \"RESULT|PASS|python:runtime|socket bind ok\"; PASS=$((PASS+1));" +
               " else echo \"RESULT|FAIL|python:runtime|socket test failed\"; FAIL=$((FAIL+1)); fi\n" +
               "if node -e \"require('http');console.log('ok')\" 2>/dev/null|grep -q ok;" +
               " then echo \"RESULT|PASS|node:runtime|http module ok\"; PASS=$((PASS+1));" +
               " else echo \"RESULT|FAIL|node:runtime|http module failed\"; FAIL=$((FAIL+1)); fi\n" +
               "echo \"SUMMARY|$PASS|$FAIL|$((PASS+FAIL))\"\n";
    }
}
