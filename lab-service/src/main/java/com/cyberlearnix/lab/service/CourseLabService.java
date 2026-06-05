package com.cyberlearnix.lab.service;

import com.cyberlearnix.lab.dto.ApprovalDecisionDto;
import com.cyberlearnix.lab.dto.CourseLabConfigRequest;
import com.cyberlearnix.lab.dto.LabRequestDto;
import com.cyberlearnix.lab.entity.ApprovalStatus;
import com.cyberlearnix.lab.entity.AssignmentStatus;
import com.cyberlearnix.lab.entity.CourseLabConfig;
import com.cyberlearnix.lab.entity.LabApprovalRequest;
import com.cyberlearnix.lab.entity.LabAssignment;
import com.cyberlearnix.lab.entity.LabTemplate;
import com.cyberlearnix.lab.repository.CourseLabConfigRepository;
import com.cyberlearnix.lab.repository.LabApprovalRequestRepository;
import com.cyberlearnix.lab.repository.LabAssignmentRepository;
import com.cyberlearnix.lab.repository.LabTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseLabService {

    private final CourseLabConfigRepository courseLabConfigRepository;
    private final LabApprovalRequestRepository approvalRequestRepository;
    private final LabAssignmentRepository assignmentRepository;
    private final LabTemplateRepository templateRepository;
    private final LabService labService;

    /**
     * Admin: link a lab template to a course.
     */
    @Transactional
    public CourseLabConfig linkTemplateToCourse(CourseLabConfigRequest req, String adminId) {
        if (courseLabConfigRepository.existsByCourseIdAndLabTemplateId(req.getCourseId(), req.getTemplateId())) {
            throw new IllegalStateException(
                    "Template " + req.getTemplateId() + " is already linked to course " + req.getCourseId());
        }

        LabTemplate template = templateRepository.findById(req.getTemplateId())
                .orElseThrow(() -> new EntityNotFoundException("Lab template not found: " + req.getTemplateId()));

        CourseLabConfig config = new CourseLabConfig();
        config.setCourseId(req.getCourseId());
        config.setLabTemplate(template);
        config.setRequiresApproval(req.getRequiresApproval() != null ? req.getRequiresApproval() : true);
        config.setIsActive(true);
        config.setCreatedBy(adminId);

        return courseLabConfigRepository.save(config);
    }

    /**
     * Get all active lab configs for a course (what labs are available).
     */
    public List<CourseLabConfig> getCourseLabConfigs(Long courseId) {
        return courseLabConfigRepository.findByCourseIdAndIsActiveTrue(courseId);
    }

    /**
     * Admin: get all active course-lab configurations across all courses.
     */
    public List<CourseLabConfig> getAllCourseLabConfigs() {
        return courseLabConfigRepository.findByIsActiveTrue();
    }

    /**
     * Instructor: request a lab for a student in a course.
     * If requiresApproval=false on the config, auto-approves immediately.
     */
    @Transactional
    public LabApprovalRequest requestLab(LabRequestDto dto, String instructorId) {
        CourseLabConfig config = courseLabConfigRepository
                .findByCourseIdAndIsActiveTrue(dto.getCourseId())
                .stream()
                .filter(c -> c.getLabTemplate().getId().equals(dto.getTemplateId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Template " + dto.getTemplateId() + " is not configured for course " + dto.getCourseId()));

        // Check for duplicate PENDING or APPROVED request for the same course+template+student
        boolean duplicate = approvalRequestRepository
                .findByCourseIdAndStudentId(dto.getCourseId(), dto.getStudentId())
                .stream()
                .anyMatch(r -> r.getLabTemplate().getId().equals(dto.getTemplateId())
                        && (r.getStatus() == ApprovalStatus.PENDING || r.getStatus() == ApprovalStatus.APPROVED));
        if (duplicate) {
            throw new IllegalStateException(
                    "Student " + dto.getStudentId() + " already has a PENDING or APPROVED request for this course+template");
        }

        LabApprovalRequest request = new LabApprovalRequest();
        request.setCourseId(dto.getCourseId());
        request.setStudentId(dto.getStudentId());
        request.setRequestedByInstructorId(instructorId);
        request.setLabTemplate(config.getLabTemplate());
        request.setStatus(ApprovalStatus.PENDING);

        if (!Boolean.TRUE.equals(config.getRequiresApproval())) {
            // Auto-approve: provision immediately
            LabAssignment assignment = labService.assignLab(dto.getStudentId(), dto.getTemplateId(), instructorId);
            assignment.setCourseId(dto.getCourseId());
            assignmentRepository.save(assignment);

            request.setStatus(ApprovalStatus.APPROVED);
            request.setDecidedAt(Instant.now());
            request.setResultingAssignmentId(assignment.getId());
            log.info("Auto-approved lab request for student {} in course {} (requiresApproval=false)",
                    dto.getStudentId(), dto.getCourseId());
        }

        return approvalRequestRepository.save(request);
    }

    /**
     * Admin: get all pending approval requests.
     */
    public List<LabApprovalRequest> getPendingApprovals() {
        return approvalRequestRepository.findByStatus(ApprovalStatus.PENDING);
    }

    /**
     * Admin: approve or reject a lab request.
     */
    @Transactional
    public LabApprovalRequest processApproval(Long requestId, ApprovalDecisionDto decision, String adminId) {
        LabApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Approval request not found: " + requestId));

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Request " + requestId + " is already " + request.getStatus());
        }

        if (Boolean.TRUE.equals(decision.getApproved())) {
            LabAssignment assignment = labService.assignLab(
                    request.getStudentId(),
                    request.getLabTemplate().getId(),
                    request.getRequestedByInstructorId());
            assignment.setCourseId(request.getCourseId());
            assignment.setApprovalRequestId(requestId);
            assignmentRepository.save(assignment);

            request.setStatus(ApprovalStatus.APPROVED);
            request.setResultingAssignmentId(assignment.getId());
            request.setApprovedByAdminId(adminId);
        } else {
            request.setStatus(ApprovalStatus.REJECTED);
            request.setRejectionReason(decision.getRejectionReason());
        }

        request.setDecidedAt(Instant.now());
        return approvalRequestRepository.save(request);
    }

    /**
     * Student: get all their lab assignments for a specific course.
     *
     * First looks for assignments explicitly linked to the course (courseId = :courseId).
     * If none are found, falls back to any RUNNING / PROVISIONING / PAUSED assignments
     * that have no course link (courseId IS NULL) — this handles labs that were assigned
     * directly by an admin before the courseId field was added.
     */
    public List<LabAssignment> getMyLabsForCourse(String studentId, Long courseId) {
        List<LabAssignment> courseSpecific = assignmentRepository.findByStudentIdAndCourseId(studentId, courseId);
        if (!courseSpecific.isEmpty()) {
            return courseSpecific;
        }
        // Fallback: active labs with no course link
        return assignmentRepository.findByStudentIdAndCourseIdIsNullAndStatusIn(
                studentId,
                List.of(AssignmentStatus.RUNNING, AssignmentStatus.PROVISIONING, AssignmentStatus.PAUSED));
    }

    /**
     * Instructor/Admin: get all approval requests for a specific course.
     */
    public List<LabApprovalRequest> getCourseRequests(Long courseId) {
        return approvalRequestRepository.findByCourseId(courseId);
    }
}
