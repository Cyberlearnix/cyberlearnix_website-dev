package com.cyberlearnix.form.controller;

import com.cyberlearnix.form.service.FormPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link FormPaymentController} covering all payment endpoints via
 * standalone MockMvc.  Verifies that callbacks inject the correct status key before
 * delegating to the service.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FormPaymentControllerTest {

    @Mock private FormPaymentService paymentService;

    @InjectMocks
    private FormPaymentController controller;

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

    // ── POST /api/forms/payments/initiate ─────────────────────────────────────

    // Guarantees: a valid initiate request returns 200 with a txnid field in the response body
    @Test
    void initiate_returns200_whenServiceSucceeds() throws Exception {
        when(paymentService.initiatePayment(any(), any(), any(), any()))
                .thenReturn(Map.of("success", true, "txnid", "FTXN123"));

        mockMvc.perform(post("/api/forms/payments/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("formResponseId", 1,
                                        "studentName",    "Bob",
                                        "studentEmail",   "bob@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txnid").value("FTXN123"));
    }

    // Guarantees: when the service throws, the endpoint returns 400 with an "error" key
    @Test
    void initiate_returns400_whenServiceThrows() throws Exception {
        when(paymentService.initiatePayment(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Payment not enabled for this form"));

        mockMvc.perform(post("/api/forms/payments/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("formResponseId", 1,
                                        "studentName",    "Bob",
                                        "studentEmail",   "bob@test.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ── POST /api/forms/payments/callback/success ─────────────────────────────

    // Guarantees: the success callback injects status="success" into the map before calling handleCallback
    @Test
    @SuppressWarnings("unchecked")
    void callbackSuccess_injectsStatusSuccess_andReturns200() throws Exception {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        when(paymentService.handleCallback(captor.capture()))
                .thenReturn(Map.of("status", "SUCCESS", "txnid", "FTXN1"));

        mockMvc.perform(post("/api/forms/payments/callback/success")
                        .param("txnid", "FTXN1")
                        .param("hash",  "somehash"))
                .andExpect(status().isOk());

        assertThat(captor.getValue().get("status")).isEqualTo("success");
    }

    // ── POST /api/forms/payments/callback/failure ─────────────────────────────

    // Guarantees: the failure callback injects status="failure" into the map before calling handleCallback
    @Test
    @SuppressWarnings("unchecked")
    void callbackFailure_injectsStatusFailure_andReturns200() throws Exception {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        when(paymentService.handleCallback(captor.capture()))
                .thenReturn(Map.of("status", "FAILURE", "txnid", "FTXN1"));

        mockMvc.perform(post("/api/forms/payments/callback/failure")
                        .param("txnid", "FTXN1")
                        .param("hash",  "somehash"))
                .andExpect(status().isOk());

        assertThat(captor.getValue().get("status")).isEqualTo("failure");
    }

    // ── POST /api/forms/payments/webhook ──────────────────────────────────────

    // Guarantees: the webhook endpoint returns 200 even when the service throws, so PayU never retries
    @Test
    void webhook_returns200_evenWhenServiceThrows() throws Exception {
        doThrow(new RuntimeException("webhook processing error"))
                .when(paymentService).handleWebhook(any());

        mockMvc.perform(post("/api/forms/payments/webhook")
                        .param("txnid", "FTXN1"))
                .andExpect(status().isOk());
    }

    // ── GET /api/forms/payments/price/{formId} ────────────────────────────────

    // Guarantees: the public price endpoint returns 200 with payment info fields
    @Test
    void getPrice_returns200_withPaymentInfo() throws Exception {
        when(paymentService.getFormPaymentInfo("form-1"))
                .thenReturn(Map.of("paymentEnabled", true, "totalAmount", 999.0));

        mockMvc.perform(get("/api/forms/payments/price/form-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentEnabled").value(true));
    }

    // ── SEC note ───────────────────────────────────────────────────────────────
    // SEC: GET /api/forms/payments/status/{txnid} carries @PreAuthorize("hasRole('ADMIN')")
    // This constraint is NOT enforced by standalone MockMvc (no security filter chain).
    // Authorisation correctness must be covered by a Spring Security integration test.

    // Guarantees: GET /api/forms/payments/status/{txnid} returns 200 with payment data when txnid is found
    @Test
    void getPaymentStatus_returns200_whenFound() throws Exception {
        when(paymentService.getPaymentStatus("FTXN-CTRL-001"))
                .thenReturn(Map.of("txnid", "FTXN-CTRL-001", "status", "SUCCESS"));

        mockMvc.perform(get("/api/forms/payments/status/FTXN-CTRL-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txnid").value("FTXN-CTRL-001"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    // Guarantees: GET /api/forms/payments/status/{txnid} returns 400 with error field when txnid is not found
    @Test
    void getPaymentStatus_returns400_whenNotFound() throws Exception {
        when(paymentService.getPaymentStatus("MISSING"))
                .thenThrow(new RuntimeException("Transaction not found"));

        mockMvc.perform(get("/api/forms/payments/status/MISSING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
