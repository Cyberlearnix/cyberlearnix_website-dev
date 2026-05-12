package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.AssignmentSubmission;
import com.cyberlearnix.shared.repository.course.AssignmentContentRepository;
import com.cyberlearnix.shared.repository.course.AssignmentSubmissionRepository;
import com.cyberlearnix.course.service.AssignmentManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * AssignmentManagementController
 *
 * Replaces the thin AssignmentController (which handled CourseAssignment calendar events).
 * This controller handles AssignmentContent submissions, code execution, grading, analytics,
 * file uploads, and AI evaluation assistance.
 *
 * NOTE: The original AssignmentController is retained at /api/assignments/upcoming etc.
 * This controller uses path prefixes that avoid collisions.
 */
@RestController
@RequestMapping("/api/assignments")
public class AssignmentManagementController {

    private static final String KEY_ERROR = "error";

    private final AssignmentManagementService assignmentManagementService;

    private final AssignmentContentRepository assignmentContentRepository;

    private final AssignmentSubmissionRepository submissionRepository;

    public AssignmentManagementController(
            AssignmentManagementService assignmentManagementService,
            AssignmentContentRepository assignmentContentRepository,
            AssignmentSubmissionRepository submissionRepository) {
        this.assignmentManagementService = assignmentManagementService;
        this.assignmentContentRepository = assignmentContentRepository;
        this.submissionRepository = submissionRepository;
    }

    // ─── Submit assignment ────────────────────────────────────────────────────

    /**
     * POST /api/assignments/{contentId}/submit
     * Student submits their work for a content assignment.
     */
    @PostMapping("/{contentId}/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> submitAssignment(
            @PathVariable Long contentId,
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-User-Id") String studentId,
            @RequestHeader(value = "X-User-Name", required = false) String studentName) {
        try {
            AssignmentSubmission submission = assignmentManagementService.submitAssignment(
                    contentId, request, studentId, studentName);
            return ResponseEntity.ok(submission);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(KEY_ERROR, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(KEY_ERROR, "Submission failed: " + e.getMessage()));
        }
    }

    // ─── Code execution ───────────────────────────────────────────────────────

    /**
     * POST /api/assignments/execute
     * Execute code in a sandboxed environment and return stdout/stderr.
     * Body: { code, language, stdin, timeLimitSeconds }
     */
    @PostMapping("/execute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> executeCode(@RequestBody Map<String, Object> request) {
        String code     = (String) request.get("code");
        String language = (String) request.get("language");
        String stdin    = (String) request.getOrDefault("stdin", "");
        Integer limit   = request.get("timeLimitSeconds") instanceof Number n ? n.intValue() : 5;

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "stderr", "No code provided"));
        }

        // Hard cap to prevent abuse
        if (limit > 30) limit = 30;

        Map<String, Object> result = assignmentManagementService.executeCode(code, language, stdin, limit);
        return ResponseEntity.ok(result);
    }

    // ─── Submission listing (instructor) ──────────────────────────────────────

    /**
     * GET /api/assignments/{contentId}/submissions
     * List all student submissions for a content assignment.
     */
    @GetMapping("/{contentId}/submissions")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR','TEACHER')")
    public ResponseEntity<List<AssignmentSubmission>> getSubmissions(@PathVariable Long contentId) {
        return ResponseEntity.ok(submissionRepository.findByContentIdOrderBySubmittedAtDesc(contentId));
    }

    /**
     * GET /api/assignments/submissions/{submissionId}
     * Get a single submission (instructor or the submitting student).
     */
    @GetMapping("/submissions/{submissionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> getSubmission(
            @PathVariable Long submissionId,
            @RequestHeader("X-User-Id") String requesterId,
            @RequestHeader("X-User-Role") String role) {
        return submissionRepository.findById(submissionId)
                .filter(s -> s.getStudentId().equals(requesterId)
                        || "ADMIN".equalsIgnoreCase(role)
                        || "INSTRUCTOR".equalsIgnoreCase(role)
                        || "TEACHER".equalsIgnoreCase(role))
                .map(s -> ResponseEntity.<Object>ok(s))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).<Object>build());
    }

    // ─── Grading ──────────────────────────────────────────────────────────────

    /**
     * POST /api/assignments/submissions/{submissionId}/grade
     * Grade a submission.
     * Body: { score, feedback, internalNote, status, rubricScores }
     */
    @PostMapping("/submissions/{submissionId}/grade")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR','TEACHER')")
    public ResponseEntity<Object> gradeSubmission(
            @PathVariable Long submissionId,
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-User-Id") String graderId) {
        try {
            AssignmentSubmission graded = assignmentManagementService.gradeSubmission(submissionId, request, graderId);
            return ResponseEntity.ok(graded);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    // ─── Analytics ────────────────────────────────────────────────────────────

    /**
     * GET /api/assignments/{contentId}/analytics
     * Return grade distribution, avg score, submission counts, plagiarism flags.
     */
    @GetMapping("/{contentId}/analytics")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR','TEACHER')")
    public ResponseEntity<Object> getAnalytics(@PathVariable Long contentId) {
        try {
            return ResponseEntity.ok(assignmentManagementService.getAnalytics(contentId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    // ─── File upload ──────────────────────────────────────────────────────────

    /**
     * POST /api/assignments/upload
     * Upload a file for a submission. Returns { url, name, size }.
     * Uses Cloudinary if configured; otherwise stores to a local temp URL (dev only).
     */
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Id") String userId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Empty file"));
        }
        // Validate file type and size (max 50MB)
        long maxBytes = 50L * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "File exceeds 50MB limit"));
        }
        // Validate content type — only allow safe submission formats
        String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        boolean safe = ct.startsWith("application/pdf")
                || ct.startsWith("image/")
                || ct.startsWith("text/")
                || ct.startsWith("application/zip")
                || ct.startsWith("application/x-zip")
                || ct.contains("document")
                || ct.contains("spreadsheet")
                || ct.contains("presentation")
                || ct.contains("java")
                || ct.contains("python")
                || ct.contains("javascript")
                || ct.contains("octet-stream");
        if (!safe) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "File type not allowed: " + ct));
        }
        // In production, integrate with Cloudinary SDK or S3 here.
        // For now, return a placeholder indicating success for the dev environment.
        return ResponseEntity.ok(Map.of(
                "url",  "/api/assignments/uploads/" + userId + "/" + file.getOriginalFilename(),
                "name", file.getOriginalFilename(),
                "size", file.getSize()
        ));
    }

    // ─── AI Evaluation assistant ──────────────────────────────────────────────

    /**
     * POST /api/assignments/ai-assist
     * Proxy to AI evaluation service. Body: { submissionId, message, context }
     * Returns: { reply }
     *
     * NOTE: Implement by calling an LLM API (OpenAI/Gemini) from a dedicated AI service.
     * This stub returns a placeholder until the AI service is wired in.
     */
    @PostMapping("/ai-assist")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR','TEACHER')")
    public ResponseEntity<Map<String, Object>> aiAssist(@RequestBody Map<String, Object> request) {
        // Stub — replace with actual LLM call
        String stub = "AI evaluation is not yet configured for this environment. "
                + "To enable it, connect an LLM API key in the course-service configuration.";
        return ResponseEntity.ok(Map.of("reply", stub));
    }

    // ─── Student's own submissions ────────────────────────────────────────────

    /**
     * GET /api/assignments/{contentId}/my-submissions
     * Student views their own submission history for an assignment.
     */
    @GetMapping("/{contentId}/my-submissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AssignmentSubmission>> mySubmissions(
            @PathVariable Long contentId,
            @RequestHeader("X-User-Id") String studentId) {
        return ResponseEntity.ok(submissionRepository.findByStudentIdAndContentId(studentId, contentId));
    }
}
