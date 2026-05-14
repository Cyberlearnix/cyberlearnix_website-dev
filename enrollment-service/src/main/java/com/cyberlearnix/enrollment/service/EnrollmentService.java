package com.cyberlearnix.enrollment.service;

import com.cyberlearnix.shared.entity.enrollment.Enrollment;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormConfig;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentSubmission;
import com.cyberlearnix.shared.repository.enrollment.*;
import com.cyberlearnix.shared.repository.form.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    @Lazy
    @Autowired
    private EnrollmentService self;

    private final EnrollmentRepository enrollmentRepository;

    private final EnrollmentSubmissionRepository submissionRepository;

    private final EnrollmentFormResponseRepository responseRepository;

    private final EnrollmentFormConfigRepository configRepository;

    private final FormValidationService validationService;

    private final com.cyberlearnix.enrollment.client.NotificationClient notificationClient;

    private final com.cyberlearnix.enrollment.client.UserClient userClient;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                              EnrollmentSubmissionRepository submissionRepository,
                              EnrollmentFormResponseRepository responseRepository,
                              EnrollmentFormConfigRepository configRepository,
                              FormValidationService validationService,
                              com.cyberlearnix.enrollment.client.NotificationClient notificationClient,
                              com.cyberlearnix.enrollment.client.UserClient userClient) {
        this.enrollmentRepository = enrollmentRepository;
        this.submissionRepository = submissionRepository;
        this.responseRepository = responseRepository;
        this.configRepository = configRepository;
        this.validationService = validationService;
        this.notificationClient = notificationClient;
        this.userClient = userClient;
    }

    public Optional<EnrollmentFormConfig> getConfig(String formId) {
        return configRepository.findById(formId);
    }

    public Optional<EnrollmentFormConfig> getConfigByToken(String formId, String token) {
        return configRepository.findByIdAndToken(formId, token);
    }

    public List<EnrollmentFormConfig> getAllActiveConfigs() {
        return configRepository.findByDeletedAtIsNull();
    }

    public List<EnrollmentFormConfig> getTrashedConfigs() {
        return configRepository.findByDeletedAtIsNotNull();
    }

    @Transactional
    public EnrollmentFormConfig saveFormConfig(EnrollmentFormConfig config) {
        if (config.getId() == null || config.getId().isEmpty()) {
            config.setId("form-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        }
        if (config.getToken() == null || config.getToken().isEmpty()) {
            config.setToken(java.util.UUID.randomUUID().toString());
        }
        config.setCreatedAt(LocalDateTime.now());
        return configRepository.save(config);
    }

    @Transactional
    public void softDeleteForm(String formId) {
        configRepository.findById(formId).ifPresent(c -> {
            c.setDeletedAt(LocalDateTime.now());
            configRepository.save(c);
        });
    }

    @Transactional
    public void restoreForm(String formId) {
        configRepository.findById(formId).ifPresent(c -> {
            c.setDeletedAt(null);
            configRepository.save(c);
        });
    }

    @Transactional
    public EnrollmentFormResponse submitResponse(EnrollmentFormResponse response) {
        // 1. Fetch Form Config
        EnrollmentFormConfig config = configRepository.findById(response.getFormId())
                .orElseThrow(() -> new RuntimeException("Form not found"));

        // 2. Check "One Response Limit"
        if (config.isLimitOneResponse()) {
            boolean alreadyResponded = responseRepository.existsByFormIdAndStudentEmailAndDeletedAtIsNull(
                    response.getFormId(), response.getStudentEmail());
            if (alreadyResponded) {
                throw new RuntimeException("You have already submitted a response for this form.");
            }
        }

        // 3. Validation
        try {
            validationService.validateResponse(config.getFields(), response.getStudentData());
        } catch (Exception e) {
            throw new RuntimeException("Validation Error: " + e.getMessage());
        }

        // 4. Save
        response.setCreatedAt(LocalDateTime.now());
        // If this form requires payment, start in PENDING state; else UNPAID (manual/free)
        response.setPaymentStatus(config.isPaymentEnabled() ? "PENDING" : "UNPAID");
        EnrollmentFormResponse saved = responseRepository.save(response);

        // 5. Trigger Confirmation Email
        try {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("recipientEmail", response.getStudentEmail());
            data.put("formTitle", config.getTitle());
            data.put("responses", response.getStudentData()); // FormValidationService ensures this is JSON
            
            notificationClient.sendNotification("send-form-confirmation", Map.of("data", data));
        } catch (Exception e) {
            log.error("Failed to send confirmation email: {}", e.getMessage());
        }

        return saved;
    }

    public Map<String, Object> getAllResponses() {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("submissions", submissionRepository.findByDeletedAtIsNull());
        result.put("formResponses", responseRepository.findByDeletedAtIsNull());
        return result;
    }

    public Map<String, Object> getTrashedResponses() {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("submissions", submissionRepository.findByDeletedAtIsNotNull());
        result.put("formResponses", responseRepository.findByDeletedAtIsNotNull());
        return result;
    }

    @Transactional
    public void softDeleteResponse(Long id, boolean isLegacy) {
        if (isLegacy) {
            submissionRepository.findById(id).ifPresent(s -> {
                s.setDeletedAt(LocalDateTime.now());
                submissionRepository.save(s);
            });
        } else {
            responseRepository.findById(id).ifPresent(r -> {
                r.setDeletedAt(LocalDateTime.now());
                responseRepository.save(r);
            });
        }
    }

    @Transactional
    public Map<String, Object> verifyPayment(Long enrollmentId, String action, String rejectionReason, String adminId, String token) {
        String status = action.equals("VERIFY") ? "VERIFIED" : "REJECTED";

        Optional<EnrollmentSubmission> submission = submissionRepository.findById(enrollmentId);
        if (submission.isPresent()) {
            EnrollmentSubmission s = submission.get();
            s.setPaymentStatus(status);
            s.setReviewedBy(adminId);
            s.setReviewedAt(LocalDateTime.now());
            s.setRejectionReason(rejectionReason);
            submissionRepository.save(s);
            return Map.of("success", true, "status", status);
        }

        Optional<EnrollmentFormResponse> response = responseRepository.findById(enrollmentId);
        if (response.isPresent()) {
            EnrollmentFormResponse r = response.get();
            r.setPaymentStatus(status);
            r.setReviewedBy(adminId);
            r.setReviewedAt(LocalDateTime.now());
            r.setRejectionReason(rejectionReason);
            responseRepository.save(r);

            if ("VERIFIED".equals(status)) {
                processVerifiedEnrollment(r, token);
            }

            return Map.of("success", true, "status", status, "formId", r.getFormId());
        }

        throw new RuntimeException("Enrollment not found");
    }

    private void processVerifiedEnrollment(EnrollmentFormResponse r, String token) {
        EnrollmentFormConfig config = configRepository.findById(r.getFormId()).orElse(null);
        List<Long> courseIdsToEnroll = (config != null) ? config.getEffectiveCourseIds() : List.of();
        String tempPassword = "Welcome@" + java.util.UUID.randomUUID().toString().substring(0, 8);
        try {
            Map<String, Object> regReq = Map.of(
                    "email", r.getStudentEmail(),
                    "password", tempPassword,
                    "role", "student"
            );
            Map<String, Object> createdUser = userClient.registerUser(token, regReq);
            String studentUuid = (createdUser != null && createdUser.get("id") != null)
                    ? (String) createdUser.get("id")
                    : null;
            enrollStudentInCourses(r, studentUuid, courseIdsToEnroll);
            notificationClient.sendNotification("send-account-credentials", Map.of(
                    "email", r.getStudentEmail(),
                    "password", tempPassword,
                    "courseTitle", (config != null ? config.getTitle() : "Course")
            ));
        } catch (Exception e) {
            log.error("Failed to automate student setup: {}", e.getMessage());
        }
    }

    private void enrollStudentInCourses(EnrollmentFormResponse r, String studentUuid, List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            log.warn("No courses linked to form {} — skipping course enrollment for {}",
                    r.getFormId(), r.getStudentEmail());
            return;
        }
        if (studentUuid != null) {
            r.setCreatedUserId(studentUuid);
            responseRepository.save(r);
            self.bulkAssign(studentUuid, courseIds);
            log.info("Enrolled student {} in {} course(s): {}", r.getStudentEmail(), courseIds.size(), courseIds);
        } else {
            log.warn("registerUser did not return an id — skipping enrollment for {}", r.getStudentEmail());
        }
    }


    @Transactional
    public List<Enrollment> bulkAssign(String studentId, List<Long> courseIds) {
        List<Enrollment> enrollments = new java.util.ArrayList<>();
        courseIds.forEach(courseId -> {
            Enrollment e = new Enrollment();
            e.setStudentId(studentId);
            e.setCourseId(courseId);
            e.setEnrolledAt(LocalDateTime.now());
            e.setProgress(0);
            enrollments.add(enrollmentRepository.save(e));
        });
        return enrollments;
    }

    @Transactional
    public EnrollmentFormConfig duplicateForm(String formId) {
        return configRepository.findById(formId).map(original -> {
            EnrollmentFormConfig copy = new EnrollmentFormConfig();
            copy.setId("form-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            copy.setTitle(original.getTitle() + " (Copy)");
            copy.setDescription(original.getDescription());
            copy.setFields(original.getFields());
            copy.setActive(false); // Default to draft
            copy.setToken(java.util.UUID.randomUUID().toString());
            copy.setStartTime(original.getStartTime());
            copy.setEndTime(original.getEndTime());
            copy.setQuiz(original.isQuiz());
            copy.setQuizSettings(original.getQuizSettings());
            copy.setLimitOneResponse(original.isLimitOneResponse());
            copy.setCreatedAt(LocalDateTime.now());
            return configRepository.save(copy);
        }).orElseThrow(() -> new RuntimeException("Original form not found"));
    }

    @Transactional
    public List<EnrollmentFormResponse> bulkSubmit(List<EnrollmentFormResponse> responses) {
        responses.forEach(r -> {
            if (r.getCreatedAt() == null)
                r.setCreatedAt(LocalDateTime.now());
            if (r.getPaymentStatus() == null)
                r.setPaymentStatus("UNPAID");
        });
        return responseRepository.saveAll(responses);
    }

    public void notifyAdminOfPayment(EnrollmentFormResponse response) {
        EnrollmentFormConfig config = configRepository.findById(response.getFormId()).orElse(null);
        if (config == null) return;

        // In a real scenario, we'd fetch the admin's email. 
        // For now, we'll notify a generic "admin" action or use a hardcoded alert email if available.
        // The user mentioned "admin should get a notification his mail".
        
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("formTitle", config.getTitle());
        data.put("studentEmail", response.getStudentEmail());
        data.put("utr", response.getTransactionId());
        data.put("amount", response.getAmountPaid());
        
        notificationClient.sendNotification("send-admin-payment-alert", java.util.Map.of("data", data));
    }
}
