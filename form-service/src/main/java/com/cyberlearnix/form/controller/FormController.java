package com.cyberlearnix.form.controller;

import com.cyberlearnix.form.dto.FormRequestDTO;
import com.cyberlearnix.form.dto.FormResponseDTO;
import com.cyberlearnix.form.dto.SubmissionResponseDTO;
import com.cyberlearnix.form.service.FormService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
public class FormController {

    private final FormService formService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<FormResponseDTO> getAllForms(@RequestParam(defaultValue = "active") String view) {
        return formService.getAllForms(view);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #token != null")
    public ResponseEntity<FormResponseDTO> getForm(
            @PathVariable String id,
            @RequestParam(required = false) String token) {
        if (token != null) {
            return ResponseEntity.ok(formService.getFormPublic(id, token));
        }
        return ResponseEntity.ok(formService.getForm(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FormResponseDTO> createForm(@Valid @RequestBody FormRequestDTO form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(formService.createForm(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FormResponseDTO updateForm(@PathVariable String id, @Valid @RequestBody FormRequestDTO form) {
        return formService.updateForm(id, form);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleActive(@PathVariable String id, @RequestBody Map<String, Boolean> payload) {
        formService.toggleActive(id, payload.get("active"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasRole('ADMIN')")
    public FormResponseDTO duplicateForm(@PathVariable String id) {
        return formService.duplicateForm(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteForm(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean permanent) {
        if (permanent) {
            formService.permanentDelete(id);
        } else {
            formService.deleteForm(id);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> restoreForm(@PathVariable String id) {
        formService.restoreForm(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/responses")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SubmissionResponseDTO> getAllResponses() {
        return formService.getAllSubmissionResponses();
    }
}
