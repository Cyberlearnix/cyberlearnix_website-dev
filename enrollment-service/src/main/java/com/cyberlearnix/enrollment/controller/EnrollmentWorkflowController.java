package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.shared.entity.enrollment.EnrollmentWorkflow;
import com.cyberlearnix.shared.repository.EnrollmentWorkflowRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/enrollments/workflows")
public class EnrollmentWorkflowController {

    private final EnrollmentWorkflowRepository workflowRepository;

    public EnrollmentWorkflowController(EnrollmentWorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @PostConstruct
    public void init() {
        initializeDefaultWorkflow();
    }

    private void initializeDefaultWorkflow() {
        if (workflowRepository.count() == 0) {
            EnrollmentWorkflow defaultWorkflow = new EnrollmentWorkflow();
            defaultWorkflow.setName("Standard Enrollment");
            defaultWorkflow.setDescription("Default enrollment process for all courses");
            defaultWorkflow.setAutoApprove(false);
            defaultWorkflow.setPaymentRequired(true);
            defaultWorkflow.setDepositAmount(500.0);
            defaultWorkflow.setIsDefault(true);
            defaultWorkflow.setIsActive(true);
            
            Map<String, Object> steps = new HashMap<>();
            steps.put("1", "Submit Application");
            steps.put("2", "Document Verification");
            steps.put("3", "Payment Processing");
            steps.put("4", "Enrollment Confirmation");
            defaultWorkflow.setSteps(steps);
            
            workflowRepository.save(defaultWorkflow);
        }
    }

    @GetMapping
    public ResponseEntity<List<EnrollmentWorkflow>> getAllWorkflows() {
        return ResponseEntity.ok(workflowRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnrollmentWorkflow> getWorkflow(@PathVariable Long id) {
        return workflowRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<EnrollmentWorkflow> createWorkflow(@RequestBody EnrollmentWorkflow workflow) {
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(workflowRepository.save(workflow));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnrollmentWorkflow> updateWorkflow(@PathVariable Long id, @RequestBody EnrollmentWorkflow updates) {
        Optional<EnrollmentWorkflow> existing = workflowRepository.findById(id);
        if (existing.isPresent()) {
            EnrollmentWorkflow workflow = existing.get();
            workflow.setName(updates.getName());
            workflow.setDescription(updates.getDescription());
            workflow.setSteps(updates.getSteps());
            workflow.setRequiredDocuments(updates.getRequiredDocuments());
            workflow.setApprovalChain(updates.getApprovalChain());
            workflow.setAutoApprove(updates.getAutoApprove());
            workflow.setPaymentRequired(updates.getPaymentRequired());
            workflow.setDepositAmount(updates.getDepositAmount());
            workflow.setCourseId(updates.getCourseId());
            workflow.setIsActive(updates.getIsActive());
            workflow.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(workflowRepository.save(workflow));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteWorkflow(@PathVariable Long id) {
        workflowRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<EnrollmentWorkflow> setDefaultWorkflow(@PathVariable Long id) {
        // Remove default from all workflows
        workflowRepository.findAll().forEach(w -> {
            w.setIsDefault(false);
            workflowRepository.save(w);
        });
        
        // Set new default
        Optional<EnrollmentWorkflow> workflow = workflowRepository.findById(id);
        if (workflow.isPresent()) {
            EnrollmentWorkflow w = workflow.get();
            w.setIsDefault(true);
            w.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(workflowRepository.save(w));
        }
        return ResponseEntity.notFound().build();
    }
}
