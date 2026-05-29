package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.ContactSubmission;
import com.cyberlearnix.shared.repository.user.ContactSubmissionRepository;
import com.cyberlearnix.user.service.EmailNotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contact-submissions")
public class ContactSubmissionController {

    @Autowired
    private ContactSubmissionRepository contactSubmissionRepository;

    @Autowired
    private EmailNotificationService emailService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ContactSubmission>> getAllSubmissions() {
        return ResponseEntity.ok(contactSubmissionRepository.findByDeletedAtIsNullOrderByCreatedAtDesc());
    }

    @PostMapping
    public ResponseEntity<ContactSubmission> createSubmission(@RequestBody ContactSubmission submission) {
        System.out.println("ContactSubmissionController: createSubmission called for: " + submission.getEmail());
        submission.setCreatedAt(LocalDateTime.now());
        submission.setStatus("unread");
        ContactSubmission saved = contactSubmissionRepository.save(submission);

        // Notify admin asynchronously (EmailNotificationService method is @Async)
        emailService.sendAdminInquiryNotification(
                saved.getName(),
                saved.getEmail(),
                saved.getPhone(),
                saved.getMessage());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContactSubmission> updateSubmission(@PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        return contactSubmissionRepository.findById(id).map(submission -> {
            if (payload.containsKey("status")) {
                submission.setStatus(payload.get("status"));
            }
            if (payload.containsKey("admin_notes")) {
                submission.setAdminNotes(payload.get("admin_notes"));
            }
            if (payload.containsKey("adminNotes")) {
                submission.setAdminNotes(payload.get("adminNotes"));
            }
            return ResponseEntity.ok(contactSubmissionRepository.save(submission));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/notes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContactSubmission> updateNotes(@PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        return contactSubmissionRepository.findById(id).map(submission -> {
            if (payload.containsKey("admin_notes")) {
                submission.setAdminNotes(payload.get("admin_notes"));
            }
            return ResponseEntity.ok(contactSubmissionRepository.save(submission));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteSubmission(@PathVariable Long id) {
        return contactSubmissionRepository.findById(id).map(submission -> {
            submission.setDeletedAt(LocalDateTime.now());
            contactSubmissionRepository.save(submission);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
