package com.cyberlearnix.lab.service;

import com.cyberlearnix.lab.entity.AssignmentStatus;
import com.cyberlearnix.lab.entity.LabAssignment;
import com.cyberlearnix.lab.entity.LabTemplate;
import com.cyberlearnix.lab.repository.LabAssignmentRepository;
import com.cyberlearnix.lab.repository.LabTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabService {

    private final LabAssignmentRepository assignmentRepository;
    private final LabTemplateRepository templateRepository;
    private final DockerClientService dockerClientService;

    @Value("${lab.defaults.idle-timeout-minutes:30}")
    private int idleTimeoutMinutes;

    /**
     * Creates a LabAssignment, provisions a container, and starts it.
     * The assignment is persisted first so the container name can include the DB id.
     */
    @Transactional
    public LabAssignment assignLab(String studentId, Long templateId, String instructorId) {
        LabTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Lab template not found: " + templateId));

        if (!Boolean.TRUE.equals(template.getIsActive())) {
            throw new IllegalStateException("Lab template is not active: " + templateId);
        }

        LabAssignment assignment = new LabAssignment();
        assignment.setStudentId(studentId);
        assignment.setInstructorId(instructorId);
        assignment.setLabTemplate(template);
        assignment.setStatus(AssignmentStatus.PROVISIONING);
        assignment.setLastActiveAt(Instant.now());
        assignment = assignmentRepository.save(assignment);

        try {
            String containerId = dockerClientService.createContainer(template, studentId, assignment.getId());
            String containerName = "cyberlearnix-lab-" + studentId + "-" + assignment.getId();
            dockerClientService.startContainer(containerId);

            assignment.setContainerId(containerId);
            assignment.setContainerName(containerName);
            assignment.setStatus(AssignmentStatus.RUNNING);
            assignment.setLastActiveAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to provision container for assignment {}: {}", assignment.getId(), e.getMessage(), e);
            assignment.setStatus(AssignmentStatus.TERMINATED);
        }

        return assignmentRepository.save(assignment);
    }

    /** Stops the container and marks the assignment as PAUSED (resumable). */
    @Transactional
    public LabAssignment stopLab(Long assignmentId) {
        LabAssignment assignment = getAssignment(assignmentId);
        if (assignment.getContainerId() != null) {
            try {
                dockerClientService.stopContainer(assignment.getContainerId());
            } catch (Exception e) {
                log.warn("Failed to stop container {} for assignment {}: {}", assignment.getContainerId(), assignmentId, e.getMessage());
            }
        }
        assignment.setStatus(AssignmentStatus.PAUSED);
        return assignmentRepository.save(assignment);
    }

    /** Stops and removes the container; marks the assignment TERMINATED (not resumable). */
    @Transactional
    public LabAssignment terminateLab(Long assignmentId) {
        LabAssignment assignment = getAssignment(assignmentId);
        if (assignment.getContainerId() != null) {
            try {
                dockerClientService.stopContainer(assignment.getContainerId());
                dockerClientService.removeContainer(assignment.getContainerId());
            } catch (Exception e) {
                log.warn("Failed to remove container {} for assignment {}: {}", assignment.getContainerId(), assignmentId, e.getMessage());
            }
        }
        assignment.setStatus(AssignmentStatus.TERMINATED);
        return assignmentRepository.save(assignment);
    }

    /** Returns the student's current RUNNING or PROVISIONING assignment, if any. */
    public Optional<LabAssignment> getStudentActiveLab(String studentId) {
        List<AssignmentStatus> activeStatuses = List.of(AssignmentStatus.RUNNING, AssignmentStatus.PROVISIONING);
        return assignmentRepository.findFirstByStudentIdAndStatusIn(studentId, activeStatuses);
    }

    /** Returns all assignments currently in RUNNING state (used by admin endpoint). */
    public List<LabAssignment> getAllRunningAssignments() {
        return assignmentRepository.findByStatus(AssignmentStatus.RUNNING);
    }

    /**
     * Scheduled cleanup: every 5 minutes, stop labs that have been idle
     * longer than {@code lab.defaults.idle-timeout-minutes}.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void cleanupIdleLabs() {
        Instant threshold = Instant.now().minus(idleTimeoutMinutes, ChronoUnit.MINUTES);
        List<LabAssignment> idleAssignments = assignmentRepository
                .findByStatusAndLastActiveAtBefore(AssignmentStatus.RUNNING, threshold);
        if (idleAssignments.isEmpty()) {
            return;
        }
        log.info("Idle lab cleanup: stopping {} idle lab(s)", idleAssignments.size());
        for (LabAssignment assignment : idleAssignments) {
            try {
                stopLab(assignment.getId());
                log.info("Stopped idle lab assignment {}", assignment.getId());
            } catch (Exception e) {
                log.error("Failed to stop idle lab assignment {}: {}", assignment.getId(), e.getMessage());
            }
        }
    }

    private LabAssignment getAssignment(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Lab assignment not found: " + assignmentId));
    }
}
