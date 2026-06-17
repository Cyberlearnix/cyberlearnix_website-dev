package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.identity.IdentityEnrollmentForm;
import com.cyberlearnix.shared.entity.identity.IdentityEnrollmentResponse;
import com.cyberlearnix.shared.entity.identity.Member;
import com.cyberlearnix.shared.entity.identity.IdentityAuditLog;
import com.cyberlearnix.user.service.IdentityService;
import com.cyberlearnix.user.service.IdCardPdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/identity")
public class IdentityController {

    @Autowired private IdentityService identityService;
    @Autowired private IdCardPdfService idCardPdfService;

    // --- Helper to get current admin email from token context ---
    private String getCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal != null ? principal.toString() : "Admin";
    }

    // ─── 1. ENROLLMENT FORMS MANAGEMENT (Admin) ───

    @GetMapping("/forms")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<List<IdentityEnrollmentForm>> getAllForms() {
        return ResponseEntity.ok(identityService.listAllForms());
    }

    @GetMapping("/forms/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IdentityEnrollmentForm> getFormById(@PathVariable String id) {
        return ResponseEntity.ok(identityService.getForm(id));
    }

    @PostMapping("/forms")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IdentityEnrollmentForm> createForm(@RequestBody IdentityEnrollmentForm form) {
        return ResponseEntity.ok(identityService.createForm(form));
    }

    @PutMapping("/forms/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IdentityEnrollmentForm> updateForm(@PathVariable String id, @RequestBody IdentityEnrollmentForm form) {
        return ResponseEntity.ok(identityService.updateForm(id, form));
    }

    @DeleteMapping("/forms/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteForm(@PathVariable String id) {
        identityService.deleteForm(id);
        return ResponseEntity.ok(Map.of("message", "Form deleted successfully"));
    }

    // ─── 2. FORM SUBMISSIONS (Public) ───

    @PostMapping("/forms/{formId}/submit")
    public ResponseEntity<IdentityEnrollmentResponse> submitResponse(
            @PathVariable String formId,
            @RequestBody IdentityEnrollmentResponse response) {
        return ResponseEntity.ok(identityService.submitResponse(formId, response));
    }

    @GetMapping("/forms/active")
    public ResponseEntity<List<IdentityEnrollmentForm>> getActiveForms() {
        return ResponseEntity.ok(identityService.listActiveForms());
    }

    @GetMapping("/forms/{id}/public")
    public ResponseEntity<IdentityEnrollmentForm> getPublicForm(@PathVariable String id) {
        IdentityEnrollmentForm form = identityService.getForm(id);
        if (!"Active".equalsIgnoreCase(form.getStatus())) {
            throw new RuntimeException("This form is inactive");
        }
        return ResponseEntity.ok(form);
    }

    // ─── 3. SUBMISSION QUEUE & APPROVAL (Admin) ───

    @GetMapping("/responses")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<List<IdentityEnrollmentResponse>> getResponses(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(identityService.listResponses(status));
    }

    @GetMapping("/responses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<IdentityEnrollmentResponse> getResponseById(@PathVariable String id) {
        return ResponseEntity.ok(identityService.getResponse(id));
    }

    @PatchMapping("/responses/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<?> reviewResponse(
            @PathVariable String id,
            @RequestBody Map<String, String> payload) {
        String status = payload.get("status"); // Approved, Rejected, ChangesRequested
        String remarks = payload.get("remarks");
        
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status is required"));
        }

        Member member = identityService.reviewResponse(id, status, remarks, getCurrentUserEmail());
        if (member != null) {
            return ResponseEntity.ok(Map.of(
                "message", "Application approved successfully",
                "memberId", member.getMemberId(),
                "member", member
            ));
        }
        
        return ResponseEntity.ok(Map.of("message", "Application status updated to " + status));
    }

    // ─── 4. MEMBERS & EMPLOYEE DIRECTORY (Admin) ───

    @GetMapping("/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Page<Member>> getMembers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(identityService.searchMembers(query, type, department, status, isActive, pageable));
    }

    @GetMapping("/members/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Member> getMemberById(@PathVariable String id) {
        return ResponseEntity.ok(identityService.getMember(id));
    }

    @PostMapping("/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Member> addMemberManually(@RequestBody Member member) {
        return ResponseEntity.ok(identityService.addMemberManually(member, getCurrentUserEmail()));
    }

    @PutMapping("/members/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Member> updateMember(@PathVariable String id, @RequestBody Member member) {
        return ResponseEntity.ok(identityService.updateMember(id, member, getCurrentUserEmail()));
    }

    @PatchMapping("/members/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Member> deactivateMember(@PathVariable String id) {
        return ResponseEntity.ok(identityService.deactivateMember(id, getCurrentUserEmail()));
    }

    @PostMapping("/members/{id}/qr")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Member> regenerateQrCode(@PathVariable String id) {
        return ResponseEntity.ok(identityService.regenerateQr(id, getCurrentUserEmail()));
    }

    @PatchMapping("/members/{id}/promote")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Member> promoteMember(
            @PathVariable String id,
            @RequestBody Map<String, String> payload) {
        String newDesignation = payload.get("designation");
        String newRoleType = payload.get("roleType");
        
        if (newDesignation == null || newDesignation.isBlank()) {
            return ResponseEntity.badRequest().body(null);
        }

        return ResponseEntity.ok(identityService.promoteMember(id, newDesignation, newRoleType, getCurrentUserEmail()));
    }

    @PatchMapping("/members/{id}/transfer")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Member> transferDepartment(
            @PathVariable String id,
            @RequestBody Map<String, String> payload) {
        String newDepartment = payload.get("department");
        
        if (newDepartment == null || newDepartment.isBlank()) {
            return ResponseEntity.badRequest().body(null);
        }

        return ResponseEntity.ok(identityService.transferDepartment(id, newDepartment, getCurrentUserEmail()));
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<List<String>> getDepartments() {
        return ResponseEntity.ok(identityService.listDepartments());
    }

    // ─── 5. PDF ID CARD GENERATION (Admin/Public) ───

    @GetMapping("/members/{id}/idcard-pdf")
    public ResponseEntity<byte[]> downloadIdCardPdf(@PathVariable String id) {
        Member member = identityService.getMember(id);
        byte[] pdfBytes = idCardPdfService.generateIdCardPdf(member);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", member.getMemberId() + "-idcard.pdf");
        headers.setContentLength(pdfBytes.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    // ─── 6. METRICS & AUDIT LOGS ───

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(identityService.getIdentityStats());
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<List<IdentityAuditLog>> getLogs() {
        return ResponseEntity.ok(identityService.getRecentLogs());
    }

    @GetMapping("/logs/{memberId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<List<IdentityAuditLog>> getLogsByMember(@PathVariable String memberId) {
        return ResponseEntity.ok(identityService.getLogsForMember(memberId));
    }
}
