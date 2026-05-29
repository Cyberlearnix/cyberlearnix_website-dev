package com.cyberlearnix.form.controller;

import com.cyberlearnix.form.dto.SubmissionRequestDTO;
import com.cyberlearnix.form.dto.SubmissionResponseDTO;
import com.cyberlearnix.form.service.FormService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forms/{formId}/responses")
@RequiredArgsConstructor
public class ResponseController {

    private final FormService formService;

    @PostMapping
    public SubmissionResponseDTO submitResponse(
            @PathVariable String formId,
            @Valid @RequestBody SubmissionRequestDTO response) {
        return formService.submitResponse(formId, response);
    }

    @GetMapping("/check")
    public ResponseEntity<?> checkResponse(
            @PathVariable String formId,
            @RequestParam String email) {
        boolean alreadyResponded = formService.hasAlreadyResponded(formId, email);
        return ResponseEntity.ok(Map.of("alreadyResponded", alreadyResponded));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<SubmissionResponseDTO> getResponses(@PathVariable String formId) {
        return formService.getSubmissionResponses(formId);
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public com.cyberlearnix.form.dto.FormAnalyticsDTO getAnalytics(@PathVariable String formId) {
        return formService.getAnalytics(formId);
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.http.ResponseEntity<String> exportCsv(@PathVariable String formId) {
        String csv = formService.exportResponsesToCsv(formId);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"responses-" + formId + ".csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv")
                .body(csv);
    }
}
