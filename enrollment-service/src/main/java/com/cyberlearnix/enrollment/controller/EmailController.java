package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.enrollment.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
public class EmailController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/send-receipt-email")
    public ResponseEntity<?> sendReceiptEmail(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-User-Id", required = true) String userId) {
        
        try {
            String to = (String) request.get("to");
            String subject = (String) request.get("subject");
            String template = (String) request.get("template");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) request.get("data");

            if (to == null || to.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email recipient (to) is required"));
            }

            if (subject == null || subject.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email subject is required"));
            }

            if (data == null || data.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email data is required"));
            }

            emailService.sendReceiptEmail(to, subject, template, data);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Receipt email sent successfully to " + to
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to send receipt email: " + e.getMessage()));
        }
    }
}
