package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.JobOpening;
import com.cyberlearnix.shared.repository.JobOpeningRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/careers")
public class CareerController {

    @Autowired
    private JobOpeningRepository jobOpeningRepository;

    @GetMapping
    public ResponseEntity<List<JobOpening>> getAllJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        if (status != null && type != null) {
            return ResponseEntity.ok(jobOpeningRepository.findByStatusAndType(status, type));
        } else if (status != null) {
            return ResponseEntity.ok(jobOpeningRepository.findByStatus(status));
        } else if (type != null) {
            return ResponseEntity.ok(jobOpeningRepository.findByType(type));
        }
        return ResponseEntity.ok(jobOpeningRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<JobOpening> createJob(@RequestBody JobOpening jobOpening) {
        System.out.println("CareerController: createJob called for: " + jobOpening.getTitle());
        return ResponseEntity.ok(jobOpeningRepository.save(jobOpening));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobOpening> updateJob(@PathVariable UUID id, @RequestBody JobOpening jobOpening) {
        return jobOpeningRepository.findById(id)
                .map(existingJob -> {
                    existingJob.setTitle(jobOpening.getTitle());
                    existingJob.setType(jobOpening.getType());
                    existingJob.setDepartment(jobOpening.getDepartment());
                    existingJob.setLocation(jobOpening.getLocation());
                    existingJob.setDescription(jobOpening.getDescription());
                    existingJob.setRequirements(jobOpening.getRequirements());
                    existingJob.setResponsibilities(jobOpening.getResponsibilities());
                    existingJob.setStatus(jobOpening.getStatus());
                    existingJob.setFormId(jobOpening.getFormId());
                    return ResponseEntity.ok(jobOpeningRepository.save(existingJob));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
        jobOpeningRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
