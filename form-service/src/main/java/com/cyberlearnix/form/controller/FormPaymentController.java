package com.cyberlearnix.form.controller;

import com.cyberlearnix.form.service.FormPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/forms/payments")
public class FormPaymentController {

    @Autowired
    private FormPaymentService paymentService;

    /**
     * Initiate payment for a form response.
     * POST /api/forms/payments/initiate
     * Body: { formResponseId, studentName, studentEmail, studentPhone }
     */
    @PostMapping("/initiate")
    public ResponseEntity<Map<String, Object>> initiatePayment(@RequestBody Map<String, Object> body) {
        try {
            Long formResponseId = Long.parseLong(String.valueOf(body.get("formResponseId")));
            String studentName = (String) body.get("studentName");
            String studentEmail = (String) body.get("studentEmail");
            String studentPhone = (String) body.getOrDefault("studentPhone", "");
            Map<String, Object> result = paymentService.initiatePayment(formResponseId, studentName, studentEmail, studentPhone);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PayU success callback (browser redirect).
     * POST /api/forms/payments/callback/success
     */
    @PostMapping("/callback/success")
    public ResponseEntity<Map<String, Object>> callbackSuccess(@RequestParam MultiValueMap<String, String> params) {
        try {
            Map<String, String> flat = params.toSingleValueMap();
            flat.put("status", "success");
            Map<String, Object> result = paymentService.handleCallback(flat);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PayU failure callback (browser redirect).
     * POST /api/forms/payments/callback/failure
     */
    @PostMapping("/callback/failure")
    public ResponseEntity<Map<String, Object>> callbackFailure(@RequestParam MultiValueMap<String, String> params) {
        try {
            Map<String, String> flat = params.toSingleValueMap();
            flat.put("status", "failure");
            Map<String, Object> result = paymentService.handleCallback(flat);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PayU server-to-server webhook.
     * POST /api/forms/payments/webhook
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestParam MultiValueMap<String, String> params) {
        try {
            paymentService.handleWebhook(params.toSingleValueMap());
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Get payment status and student details by txnid (admin only).
     * GET /api/forms/payments/status/{txnid}
     */
    @GetMapping("/status/{txnid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable String txnid) {
        try {
            return ResponseEntity.ok(paymentService.getPaymentStatus(txnid));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get form payment configuration (public endpoint).
     * GET /api/forms/payments/price/{formId}
     */
    @GetMapping("/price/{formId}")
    public ResponseEntity<Map<String, Object>> getFormPaymentInfo(@PathVariable String formId) {
        try {
            return ResponseEntity.ok(paymentService.getFormPaymentInfo(formId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
