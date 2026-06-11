package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.enrollment.service.PaymentService;
import com.cyberlearnix.shared.entity.enrollment.PaymentTransaction;
import com.cyberlearnix.shared.repository.enrollment.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * PayU Payment Gateway endpoints.
 *
 * Flow:
 *   1.  POST /api/enrollments/payments/initiate          → get PayU form fields + hash
 *   2.  Browser submits the form to PayU (action URL from step 1)
 *   3a. PayU browser redirect → POST /api/enrollments/payments/callback/success  (surl)
 *   3b. PayU browser redirect → POST /api/enrollments/payments/callback/failure  (furl)
 *   4.  PayU server webhook   → POST /api/enrollments/payments/webhook
 *   5.  Frontend polls        → GET  /api/enrollments/payments/status/{txnid}
 *   6.  Frontend polls        → GET  /api/enrollments/payments/status/response/{responseId}
 */
@RestController
@RequestMapping("/api/enrollments/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_STATUS = "status";

    private final PaymentService paymentService;

    private final PaymentTransactionRepository transactionRepository;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public PaymentController(
            PaymentService paymentService,
            PaymentTransactionRepository transactionRepository) {
        this.paymentService = paymentService;
        this.transactionRepository = transactionRepository;
    }

    // ── 1. Initiate ───────────────────────────────────────────────────────────

    /**
     * Called by the frontend after the student submits the enrollment form.
     * Returns the PayU form parameters including the signed hash.
     *
     * Request body:
     * {
     *   "formResponseId": 123,
     *   "studentName":    "John Doe",
     *   "studentEmail":   "john@example.com",
     *   "studentPhone":   "9876543210"   // optional
     * }
     */
    @PostMapping("/initiate")
    public ResponseEntity<Map<String, Object>> initiatePayment(@RequestBody Map<String, Object> payload) {
        try {
            Long formResponseId = Long.valueOf(String.valueOf(payload.get("formResponseId")));
            String studentName  = (String) payload.get("studentName");
            String studentEmail = (String) payload.get("studentEmail");
            String studentPhone = (String) payload.getOrDefault("studentPhone", "");
            String couponCode   = (String) payload.getOrDefault("couponCode", null);

            Map<String, Object> result = paymentService.initiatePayment(
                    formResponseId, studentName, studentEmail, studentPhone, couponCode);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    KEY_SUCCESS, false,
                    KEY_MESSAGE, e.getMessage()));
        }
    }

    // ── 2. PayU Success Callback (browser redirect) ───────────────────────────

    /**
     * PayU redirects the student's browser here after a SUCCESSFUL payment.
     * Verifies the hash and marks the transaction + form response as PAID.
     * Redirects to the frontend with status parameters.
     */
    @CrossOrigin(origins = "*", allowCredentials = "false")
    @RequestMapping(value = "/callback/success", method = {RequestMethod.GET, RequestMethod.POST})
    @SuppressWarnings("unchecked")
    public ResponseEntity<Object> paymentSuccess(@RequestParam Map<String, String> params) {
        try {
            Map<String, Object> result = paymentService.handleCallback(params);
            return (ResponseEntity<Object>) (ResponseEntity<?>) redirectToFrontend(result);
        } catch (Exception e) {
            log.error("[PayU] Success callback processing error: {}", e.getMessage(), e);
            return (ResponseEntity<Object>) (ResponseEntity<?>) redirectToFrontendError("failure", "callback_error");
        }
    }

    // ── 3. PayU Failure Callback (browser redirect) ───────────────────────────

    /**
     * PayU redirects the student's browser here after a FAILED/CANCELLED payment.
     */
    @CrossOrigin(origins = "*", allowCredentials = "false")
    @RequestMapping(value = "/callback/failure", method = {RequestMethod.GET, RequestMethod.POST})
    @SuppressWarnings("unchecked")
    public ResponseEntity<Object> paymentFailure(@RequestParam Map<String, String> params) {
        try {
            Map<String, Object> result = paymentService.handleCallback(params);
            return (ResponseEntity<Object>) (ResponseEntity<?>) redirectToFrontend(result);
        } catch (Exception e) {
            log.error("[PayU] Failure callback processing error: {}", e.getMessage(), e);
            return (ResponseEntity<Object>) (ResponseEntity<?>) redirectToFrontendError("failure", "callback_error");
        }
    }

    // ── 4. PayU Server-to-Server Webhook ──────────────────────────────────────

    /**
     * PayU calls this directly (not via browser) for guaranteed delivery.
     * Must return 200 OK; PayU retries on failure.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> paymentWebhook(@RequestParam Map<String, String> params) {
        try {
            paymentService.handleWebhook(params);
            return ResponseEntity.ok(Map.of(KEY_STATUS, "OK"));
        } catch (Exception e) {
            // Log but still return 200 to avoid PayU retrying
            log.error("[PayU Webhook] Error: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(KEY_STATUS, "OK"));
        }
    }

    // ── 5. Status: by txnid ───────────────────────────────────────────────────

    @GetMapping("/status/{txnid}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String txnid) {
        try {
            return ResponseEntity.ok(paymentService.getPaymentStatus(txnid));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── 6. Status: by form response ID ────────────────────────────────────────

    @GetMapping("/status/response/{responseId}")
    public ResponseEntity<Map<String, Object>> getStatusByResponse(@PathVariable Long responseId) {
        return ResponseEntity.ok(paymentService.getStatusByResponseId(responseId));
    }

    // ── 7. List transactions for a form (admin) ───────────────────────────────

    // SEC-003: Restrict to ADMIN — payment data contains PII (name, email, amount)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/form/{formId}")
    public ResponseEntity<List<PaymentTransaction>> getByForm(@PathVariable String formId) {
        return ResponseEntity.ok(transactionRepository.findByFormId(formId));
    }

    // ── Legacy endpoint (kept for backward compatibility) ─────────────────────

    /**
     * @deprecated Use POST /api/enrollments/payments/initiate instead.
     */
    @Deprecated(since = "1.0", forRemoval = true)
    @PostMapping("/payu-payment")
    public ResponseEntity<Map<String, Object>> initiatePaymentLegacy(@RequestBody Map<String, Object> payload) {
        return initiatePayment(payload);
    }

    private ResponseEntity<Void> redirectToFrontend(Map<String, Object> result) {
        String status = String.valueOf(result.getOrDefault("status", "FAILURE")).toLowerCase();
        String formId = encode(String.valueOf(result.getOrDefault("formId", "")));
        String txnid = encode(String.valueOf(result.getOrDefault("txnid", "")));
        String responseId = encode(String.valueOf(result.getOrDefault("responseId", "")));
        String email = encode(String.valueOf(result.getOrDefault("email", "")));
        String redirectUrl = frontendUrl + "/enroll-form.html?status=" + status
                + "&formId=" + formId
                + "&txnid=" + txnid
                + "&responseId=" + responseId
                + "&email=" + email;

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(redirectUrl));
        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private ResponseEntity<Void> redirectToFrontendError(String status, String reason) {
        String redirectUrl = frontendUrl + "/enroll-form.html?status=" + encode(status)
                + "&reason=" + encode(reason);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(redirectUrl));
        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
    }
}

