package com.cyberlearnix.attendance.controller;

import com.cyberlearnix.attendance.dto.ZohoWebhookEvent;
import com.cyberlearnix.attendance.service.ZohoWebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "Webhooks", description = "Zoho Meeting webhook endpoints")
@RestController
@RequestMapping("/api/attendance/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private static final String KEY_STATUS = "status";
    private static final String KEY_ERROR = "error";

    private final ZohoWebhookService webhookService;
    private final ObjectMapper objectMapper;

    @Value("${zoho.meeting.webhook-token:}")
    private String webhookToken;

    /**
     * Receives Zoho Meeting webhook events.
     * Zoho sends a token in the X-Zoho-Meeting-Token header for validation.
     */
    @Operation(summary = "Receive Zoho Meeting webhook")
    @PostMapping("/zoho")
    public ResponseEntity<Map<String, String>> receiveZohoWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Zoho-Meeting-Token", required = false) String token,
            HttpServletRequest request) {

        // Validate webhook token
        if (webhookToken != null && !webhookToken.isBlank()
                && !webhookToken.equals(token)) {
            log.warn("Zoho webhook: invalid token from IP {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(KEY_ERROR, "unauthorized"));
        }

        try {
            ZohoWebhookEvent event = objectMapper.readValue(rawPayload, ZohoWebhookEvent.class);
            log.info("Received Zoho webhook event: {} from {}", event.getEvent(), request.getRemoteAddr());
            webhookService.processEvent(event, rawPayload, request.getRemoteAddr());
            return ResponseEntity.ok(Map.of(KEY_STATUS, "processed"));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Zoho webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "invalid payload"));
        } catch (Exception e) {
            log.error("Error processing Zoho webhook: {}", e.getMessage(), e);
            // Return 200 to avoid Zoho retrying indefinitely
            return ResponseEntity.ok(Map.of(KEY_STATUS, "error logged"));
        }
    }

    /**
     * Verification endpoint for Zoho webhook setup.
     */
    @GetMapping("/zoho/verify")
    public ResponseEntity<Map<String, String>> verify(
            @RequestParam(required = false) String token) {
        return ResponseEntity.ok(Map.of(KEY_STATUS, "ok", "service", "cyberlearnix-attendance"));
    }
}
