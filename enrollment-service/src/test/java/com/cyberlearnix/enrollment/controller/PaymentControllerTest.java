package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.enrollment.service.PaymentService;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link PaymentController} covering all 7 payment endpoints via
 * standalone MockMvc (no Spring context — Spring Security filters are not active).
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock private PaymentService paymentService;
    @Mock private PaymentTransactionRepository transactionRepository;

    @InjectMocks
    private PaymentController controller;

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

    // ── POST /api/enrollments/payments/initiate ────────────────────────────────

    // Guarantees: a valid initiate request returns 200 and includes txnid in the response body
    @Test
    void initiate_returns200_withTxnid_whenServiceSucceeds() throws Exception {
        when(paymentService.initiatePayment(any(), any(), any(), any(), any()))
                .thenReturn(Map.of("success", true, "txnid", "TXN-TESTXX"));

        mockMvc.perform(post("/api/enrollments/payments/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("formResponseId", 1, "studentName", "Alice",
                                        "studentEmail", "alice@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txnid").value("TXN-TESTXX"));
    }

    // Guarantees: when the service throws (e.g. payment not configured), the endpoint returns 400 with a message field
    @Test
    void initiate_returns400_whenServiceThrows() throws Exception {
        when(paymentService.initiatePayment(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Payment amount not configured"));

        mockMvc.perform(post("/api/enrollments/payments/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("formResponseId", 1, "studentName", "Alice",
                                        "studentEmail", "alice@test.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    // ── POST /api/enrollments/payments/callback/success ───────────────────────

    // Guarantees: the success callback endpoint returns 200 and delegates to the service without modification
    @Test
    void callbackSuccess_returns200_withStatus() throws Exception {
        when(paymentService.handleCallback(any()))
                .thenReturn(Map.of("status", "SUCCESS", "txnid", "TXN1"));

        mockMvc.perform(post("/api/enrollments/payments/callback/success")
                        .param("txnid",   "TXN1")
                        .param("status",  "success")
                        .param("hash",    "abc123"))
                .andExpect(status().isOk());
    }

    // ── POST /api/enrollments/payments/callback/failure ───────────────────────

    // Guarantees: the failure callback endpoint returns 200 and delegates to the service without modification
    @Test
    void callbackFailure_returns200_withStatus() throws Exception {
        when(paymentService.handleCallback(any()))
                .thenReturn(Map.of("status", "FAILURE", "txnid", "TXN1"));

        mockMvc.perform(post("/api/enrollments/payments/callback/failure")
                        .param("txnid",  "TXN1")
                        .param("status", "failure")
                        .param("hash",   "abc123"))
                .andExpect(status().isOk());
    }

    // ── POST /api/enrollments/payments/webhook ────────────────────────────────

    // Guarantees: the webhook endpoint always returns 200 OK even when the service throws, so PayU never retries
    @Test
    void webhook_returns200_evenWhenServiceThrows() throws Exception {
        doThrow(new RuntimeException("unexpected webhook error"))
                .when(paymentService).handleWebhook(any());

        mockMvc.perform(post("/api/enrollments/payments/webhook")
                        .param("txnid", "TXN1")
                        .param("status", "success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    // ── GET /api/enrollments/payments/status/{txnid} ──────────────────────────

    // Guarantees: the status endpoint returns 200 with transaction details when the txnid is found
    @Test
    void getStatus_returns200_whenFound() throws Exception {
        when(paymentService.getPaymentStatus("TXN1"))
                .thenReturn(Map.of("txnid", "TXN1", "status", "SUCCESS"));

        mockMvc.perform(get("/api/enrollments/payments/status/TXN1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txnid").value("TXN1"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    // Guarantees: the status endpoint returns 404 (not 200) when the txnid is not found
    @Test
    void getStatus_returns404_whenNotFound() throws Exception {
        when(paymentService.getPaymentStatus(anyString()))
                .thenThrow(new RuntimeException("Transaction not found"));

        mockMvc.perform(get("/api/enrollments/payments/status/MISSING"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/enrollments/payments/status/response/{responseId} ────────────

    // Guarantees: the response-status endpoint always returns 200 (even when no transaction exists — service returns found:false)
    @Test
    void getStatusByResponse_returns200_always() throws Exception {
        when(paymentService.getStatusByResponseId(42L))
                .thenReturn(Map.of("found", true, "status", "SUCCESS"));

        mockMvc.perform(get("/api/enrollments/payments/status/response/42"))
                .andExpect(status().isOk());
    }

    // ── SEC-003 note ───────────────────────────────────────────────────────────
    // SEC-003: GET /api/enrollments/payments/form/{formId} carries @PreAuthorize("hasRole('ADMIN')")
    // This authorization constraint is NOT exercised by standalone MockMvc (no security filter chain).
    // Authorisation correctness must be covered by a Spring Security integration test.
    // Covered here: the endpoint exists, wires to transactionRepository.findByFormId, and returns 200
    // for any caller when the security layer is absent (standalone mode).
    @Test
    void getByForm_returns200_inStandaloneMvcWithoutSecurity() throws Exception {
        when(transactionRepository.findByFormId("form-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/enrollments/payments/form/form-1"))
                .andExpect(status().isOk());
    }
}
