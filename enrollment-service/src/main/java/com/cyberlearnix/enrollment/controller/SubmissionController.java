package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.shared.entity.enrollment.EnrollmentSubmission;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentSubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments/submissions")
public class SubmissionController {

    @Autowired
    private EnrollmentSubmissionRepository submissionRepository;

    /**
     * GET /api/enrollments/submissions
     * Returns all active (non-deleted) submissions. Used by applications-admin.js
     */
    @GetMapping
    public ResponseEntity<List<EnrollmentSubmission>> getSubmissions(
            @RequestParam(required = false) String status) {
        List<EnrollmentSubmission> submissions;
        if (status != null) {
            submissions = submissionRepository.findByDeletedAtIsNull().stream()
                    .filter(s -> status.equalsIgnoreCase(s.getStatus()))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            submissions = submissionRepository.findByDeletedAtIsNull();
        }
        return ResponseEntity.ok(submissions);
    }

    /**
     * POST /api/enrollments/submissions
     * Create a new submission (used by the enrollment form).
     */
    @PostMapping
    public ResponseEntity<EnrollmentSubmission> createSubmission(@RequestBody EnrollmentSubmission submission) {
        submission.setStatus("pending");
        submission.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(submissionRepository.save(submission));
    }

    @Autowired
    private com.cyberlearnix.enrollment.service.EnrollmentService enrollmentService;

    /**
     * PATCH /api/enrollments/submissions/{id}/status
     * Update the status of a submission (e.g., approve or reject).
     * Used by applications-admin.js approve/reject actions.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return submissionRepository.findById(id).map(submission -> {
            String newStatus = payload.get("status");
            submission.setStatus(newStatus);
            submission.setReviewedBy(adminId);
            submission.setReviewedAt(LocalDateTime.now());
            if (payload.containsKey("rejectionReason")) {
                submission.setRejectionReason(payload.get("rejectionReason"));
            }
            if ("approved".equalsIgnoreCase(newStatus) || "verified".equalsIgnoreCase(newStatus)) {
                submission.setPaymentStatus("VERIFIED");
            } else if ("rejected".equalsIgnoreCase(newStatus)) {
                submission.setPaymentStatus("REJECTED");
            }
            submissionRepository.save(submission);

            if ("approved".equalsIgnoreCase(newStatus) || "verified".equalsIgnoreCase(newStatus)) {
                enrollmentService.processVerifiedSubmission(submission, token);
            }

            return ResponseEntity.ok(Map.of("success", true, "status", newStatus));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/enrollments/submissions/{id}
     * Soft-delete a submission.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSubmission(@PathVariable Long id) {
        return submissionRepository.findById(id).map(submission -> {
            submission.setDeletedAt(LocalDateTime.now());
            submissionRepository.save(submission);
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.notFound().build());
    }
}
