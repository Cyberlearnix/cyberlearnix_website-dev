package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.enrollment.service.EnrollmentService;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormConfig;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormResponseRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentSubmissionRepository;
import com.cyberlearnix.shared.repository.enrollment.PaymentTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for GET /api/enrollments/responses/admin-view in {@link ResponseController}.
 *
 * Uses standalone MockMvc (no Spring context) for consistency with existing controller tests.
 * Shiva's implementation is already merged:
 *   - ResponseController.getAdminView() endpoint (with @PreAuthorize("hasRole('ADMIN')"))
 *   - EnrollmentFormResponse.paymentMode + mihpayid fields
 *   - PaymentService.handleCallback() sets paymentMode via resolvePaymentMode()
 *
 * Security tests (403/401) require @WebMvcTest to enforce Spring Security's @PreAuthorize.
 * In standalone MockMvc those tests document the access-control contract and currently
 * fail because @PreAuthorize is not evaluated without a Spring Security context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ResponseControllerAdminViewTest {

    @Mock private EnrollmentService enrollmentService;
    @Mock private EnrollmentSubmissionRepository submissionRepository;
    @Mock private EnrollmentFormResponseRepository responseRepository;
    @Mock private EnrollmentFormConfigRepository configRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;

    @InjectMocks
    private ResponseController controller;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EnrollmentFormResponse buildPaidResponse() {
        EnrollmentFormResponse r = new EnrollmentFormResponse();
        r.setId(1L);
        r.setFormId("form-1");
        r.setStudentEmail("bob@test.com");
        r.setPaymentStatus("PAID");
        r.setTransactionId("TXN-001");
        r.setAmountPaid(999.0);
        r.setPaymentMode("UPI");  // set by handleCallback via resolvePaymentMode()
        return r;
    }

    private EnrollmentFormResponse buildPendingResponse() {
        EnrollmentFormResponse r = new EnrollmentFormResponse();
        r.setId(2L);
        r.setFormId("form-1");
        r.setStudentEmail("carol@test.com");
        r.setPaymentStatus("PENDING");
        r.setAmountPaid(0.0);
        return r;
    }

    private EnrollmentFormConfig buildFormConfig() {
        EnrollmentFormConfig config = new EnrollmentFormConfig();
        config.setId("form-1");
        config.setTitle("Java Bootcamp");
        return config;
    }

    // ── Security tests ────────────────────────────────────────────────────────

    // Guarantees: GET /admin-view returns 200 for a caller with ADMIN role.
    // The endpoint exists and @PreAuthorize("hasRole('ADMIN')") is not evaluated in standalone MockMvc,
    // so this passes for any caller — it documents that the endpoint returns the right shape for ADMIN.
    @Test
    void getAdminView_returns200_whenAdminRole() throws Exception {
        when(responseRepository.findByDeletedAtIsNull()).thenReturn(List.of(buildPaidResponse()));
        when(configRepository.findById("form-1")).thenReturn(Optional.of(buildFormConfig()));

        mockMvc.perform(get("/api/enrollments/responses/admin-view")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    // Guarantees: GET /admin-view returns 403 when caller has STUDENT role (non-admin).
    // The endpoint uses @PreAuthorize("hasRole('ADMIN')") — enforced by Spring Security.
    // NOTE: This test requires @WebMvcTest (full Spring Security context) to pass.
    //       In standalone MockMvc, @PreAuthorize is not evaluated — returns 200, not 403.
    // TODO: Move to a @WebMvcTest variant to make this green.
    @Test
    void getAdminView_returns403_whenStudentRole() throws Exception {
        mockMvc.perform(get("/api/enrollments/responses/admin-view")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());
    }

    // Guarantees: GET /admin-view returns 401 when no authentication header is present.
    // The endpoint uses @PreAuthorize("hasRole('ADMIN')") — enforced by Spring Security.
    // NOTE: This test requires @WebMvcTest (full Spring Security context) to pass.
    //       In standalone MockMvc, @PreAuthorize is not evaluated — returns 200, not 401.
    // TODO: Move to a @WebMvcTest variant to make this green.
    @Test
    void getAdminView_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/enrollments/responses/admin-view"))
                .andExpect(status().isUnauthorized());
    }

    // ── Functional tests ──────────────────────────────────────────────────────

    // Guarantees: when ?paymentStatus=PAID is supplied, only PAID records are returned in the response array.
    @Test
    void getAdminView_filtersByPaymentStatus_whenParamProvided() throws Exception {
        when(responseRepository.findByDeletedAtIsNull())
                .thenReturn(List.of(buildPaidResponse(), buildPendingResponse()));
        when(configRepository.findById("form-1")).thenReturn(Optional.of(buildFormConfig()));

        mockMvc.perform(get("/api/enrollments/responses/admin-view")
                        .param("paymentStatus", "PAID")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].paymentStatus").value("PAID"));
    }

    // Guarantees: each item in the admin-view response includes a non-null courseTitle field
    // derived from the EnrollmentFormConfig.title linked to the response's formId.
    @Test
    void getAdminView_includesCourseTitle_inResponse() throws Exception {
        when(responseRepository.findByDeletedAtIsNull()).thenReturn(List.of(buildPaidResponse()));
        when(configRepository.findById("form-1")).thenReturn(Optional.of(buildFormConfig()));

        mockMvc.perform(get("/api/enrollments/responses/admin-view")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseTitle").value("Java Bootcamp"));
    }

    // Guarantees: each item in the admin-view response includes a non-null paymentMode field.
    // paymentMode is set by PaymentService.handleCallback() via resolvePaymentMode() and stored
    // on EnrollmentFormResponse.paymentMode; the admin-view endpoint surfaces it directly.
    @Test
    void getAdminView_includesPaymentMode_inResponse() throws Exception {
        when(responseRepository.findByDeletedAtIsNull()).thenReturn(List.of(buildPaidResponse()));
        when(configRepository.findById("form-1")).thenReturn(Optional.of(buildFormConfig()));
        // Stub transaction lookup so the NPE from unmocked repo is avoided.
        when(paymentTransactionRepository.findTopByFormResponseIdOrderByInitiatedAtDesc(1L))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/enrollments/responses/admin-view")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentMode").value("UPI"));
    }
}
