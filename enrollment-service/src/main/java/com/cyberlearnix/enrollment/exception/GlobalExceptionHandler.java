package com.cyberlearnix.enrollment.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles validation errors thrown by FormValidationService
     * (e.g. "Validation Error: Invalid email format for field 'gsdfg'").
     * Returns HTTP 400 with a structured JSON body.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage();

        // Validation errors are business-level 400s, not server errors
        HttpStatus status = (message != null && message.startsWith("Validation Error:"))
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.INTERNAL_SERVER_ERROR;

        // SEC-004: Log full exception internally but never expose stack traces or internal
        // messages (DB errors, Feign details) to callers in 5xx responses.
        String responseMessage;
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("Unhandled exception in enrollment-service", ex);
            responseMessage = "An unexpected error occurred. Please try again later.";
        } else {
            responseMessage = message;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", responseMessage);

        return ResponseEntity.status(status).body(body);
    }
}
