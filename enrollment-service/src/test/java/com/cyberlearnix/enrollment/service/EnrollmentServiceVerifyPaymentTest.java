package com.cyberlearnix.enrollment.service;

import com.cyberlearnix.enrollment.client.NotificationClient;
import com.cyberlearnix.enrollment.client.UserClient;
import com.cyberlearnix.shared.entity.enrollment.Enrollment;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormConfig;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormResponseRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentSubmissionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EnrollmentService#verifyPayment} covering BUG-001:
 * after calling userClient.registerUser(), the service must use the returned
 * "id" UUID — not the student email — when calling bulkAssign().
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceVerifyPaymentTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EnrollmentSubmissionRepository submissionRepository;
    @Mock private EnrollmentFormResponseRepository responseRepository;
    @Mock private EnrollmentFormConfigRepository configRepository;
    @Mock private FormValidationService validationService;
    @Mock private NotificationClient notificationClient;
    @Mock private UserClient userClient;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private EnrollmentFormResponse buildFormResponse(String email, String formId) {
        EnrollmentFormResponse r = new EnrollmentFormResponse();
        r.setId(1L);
        r.setFormId(formId);
        r.setStudentEmail(email);
        r.setPaymentStatus("PENDING");
        return r;
    }

    private EnrollmentFormConfig buildFormConfig(String formId, Long courseId) {
        EnrollmentFormConfig c = new EnrollmentFormConfig();
        c.setId(formId);
        c.setTitle("Test Course");
        c.setCourseId(courseId);
        return c;
    }

    // Guarantees: when registerUser returns a map containing "id", bulkAssign uses that UUID — not the student email
    @Test
    void verifyPayment_usesReturnedUserUuid_notEmail_forBulkAssign() {
        EnrollmentFormResponse response = buildFormResponse("student@test.com", "form-1");
        EnrollmentFormConfig config = buildFormConfig("form-1", 5L);

        when(submissionRepository.findById(1L)).thenReturn(Optional.empty());
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-1")).thenReturn(Optional.of(config));
        when(userClient.registerUser(anyString(), anyMap()))
                .thenReturn(Map.of("id", "uuid-student-abc"));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        enrollmentService.verifyPayment(1L, "VERIFY", null, "admin-1", "Bearer token");

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(captor.capture());

        assertThat(captor.getValue().getStudentId())
                .as("studentId must be the UUID from registerUser, not the student email")
                .isEqualTo("uuid-student-abc")
                .isNotEqualTo("student@test.com");
    }

    // Guarantees: when registerUser returns a map with no "id" key, enrollment is skipped and no save is called
    @Test
    void verifyPayment_skipsEnrollment_whenRegisteredUserMapHasNoId() {
        EnrollmentFormResponse response = buildFormResponse("student@test.com", "form-1");
        EnrollmentFormConfig config = buildFormConfig("form-1", 5L);

        when(submissionRepository.findById(1L)).thenReturn(Optional.empty());
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-1")).thenReturn(Optional.of(config));
        when(userClient.registerUser(anyString(), anyMap()))
                .thenReturn(Map.of()); // no "id" key — simulates a broken/empty response

        enrollmentService.verifyPayment(1L, "VERIFY", null, "admin-1", "Bearer token");

        verify(enrollmentRepository, never()).save(any());
    }

    // Guarantees: when registerUser returns null, enrollment is skipped and no save is called
    @Test
    void verifyPayment_skipsEnrollment_whenRegisteredUserMapIsNull() {
        EnrollmentFormResponse response = buildFormResponse("student@test.com", "form-1");
        EnrollmentFormConfig config = buildFormConfig("form-1", 5L);

        when(submissionRepository.findById(1L)).thenReturn(Optional.empty());
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-1")).thenReturn(Optional.of(config));
        when(userClient.registerUser(anyString(), anyMap())).thenReturn(null);

        enrollmentService.verifyPayment(1L, "VERIFY", null, "admin-1", "Bearer token");

        verify(enrollmentRepository, never()).save(any());
    }

    // Guarantees: a REJECT action never triggers user creation or enrollment
    @Test
    void verifyPayment_doesNotCreateUserOrEnroll_whenActionIsReject() {
        EnrollmentFormResponse response = buildFormResponse("student@test.com", "form-1");

        when(submissionRepository.findById(1L)).thenReturn(Optional.empty());
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));

        enrollmentService.verifyPayment(1L, "REJECT", "Payment declined", "admin-1", "Bearer token");

        verify(userClient, never()).registerUser(anyString(), anyMap());
        verify(enrollmentRepository, never()).save(any());
    }
}
