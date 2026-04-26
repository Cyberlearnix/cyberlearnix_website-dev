package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.enrollment.service.EnrollmentService;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormConfig;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments/forms")
public class FormController {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private EnrollmentFormConfigRepository configRepository;

    @GetMapping
    public ResponseEntity<List<EnrollmentFormConfig>> getAllConfigs(@RequestParam(required = false) String view) {
        if ("trash".equals(view)) {
            return ResponseEntity.ok(enrollmentService.getTrashedConfigs());
        }
        return ResponseEntity.ok(enrollmentService.getAllActiveConfigs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnrollmentFormConfig> getConfig(@PathVariable String id,
            @RequestParam(required = false) String token) {
        if (token != null) {
            return enrollmentService.getConfigByToken(id, token)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return configRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<EnrollmentFormConfig> createConfig(@RequestBody EnrollmentFormConfig config) {
        return ResponseEntity.ok(enrollmentService.saveFormConfig(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnrollmentFormConfig> updateConfig(@PathVariable String id,
            @RequestBody EnrollmentFormConfig updates) {
        return configRepository.findById(id).map(config -> {
            config.setTitle(updates.getTitle());
            config.setCourseId(updates.getCourseId());
            if (updates.getEnrolleeRole() != null) config.setEnrolleeRole(updates.getEnrolleeRole());
            config.setFields(updates.getFields());
            config.setDescription(updates.getDescription());
            config.setActive(updates.isActive());
            config.setStartTime(updates.getStartTime());
            config.setEndTime(updates.getEndTime());
            config.setQuiz(updates.isQuiz());
            config.setQuizSettings(updates.getQuizSettings());
            config.setLimitOneResponse(updates.isLimitOneResponse());
            if (updates.getPaymentEnabled() != null) config.setPaymentEnabled(updates.getPaymentEnabled());
            if (updates.getPaymentAmount() != null) config.setPaymentAmount(updates.getPaymentAmount());
            if (updates.getPaymentCurrency() != null) config.setPaymentCurrency(updates.getPaymentCurrency());
            return ResponseEntity.ok(configRepository.save(config));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteConfig(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean permanent) {
        if (permanent) {
            configRepository.deleteById(id);
        } else {
            enrollmentService.softDeleteForm(id);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restoreConfig(@PathVariable String id) {
        enrollmentService.restoreForm(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<?> toggleActive(@PathVariable String id, @RequestBody Map<String, Boolean> payload) {
        return configRepository.findById(id).map(config -> {
            config.setActive(payload.get("active"));
            configRepository.save(config);
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<EnrollmentFormConfig> duplicateConfig(@PathVariable String id) {
        return ResponseEntity.ok(enrollmentService.duplicateForm(id));
    }
}
