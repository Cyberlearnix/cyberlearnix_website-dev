package com.cyberlearnix.enrollment.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", HttpStatus.FORBIDDEN.getReasonPhrase());
        body.put("message", "Access denied");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.put("message", "Malformed or invalid request body");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles business-logic and validation errors from the enrollment service.
     * Returns appropriate 4xx status codes for predictable errors; 500 for unexpected ones.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage();

        HttpStatus status;
        String responseMessage;

        if (message != null && message.startsWith("Validation Error:")) {
            status = HttpStatus.BAD_REQUEST;
            responseMessage = message;
        } else if (message != null && (message.contains("already submitted") || message.contains("already responded"))) {
            status = HttpStatus.CONFLICT;
            responseMessage = message;
        } else if (message != null && message.equals("Form not found")) {
            status = HttpStatus.NOT_FOUND;
            responseMessage = "The requested form was not found.";
        } else {
            // SEC-004: Log full exception internally but never expose stack traces or internal
            // messages (DB errors, Feign details) to callers in 5xx responses.
            log.error("Unhandled exception in enrollment-service", ex);
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            responseMessage = "An unexpected error occurred. Please try again later.";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", responseMessage);

        return ResponseEntity.status(status).body(body);
    }
}
