package com.cyberlearnix.enrollment.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link GlobalExceptionHandler} covering SEC-004: internal
 * exception details (stack traces, DB errors, Feign messages) must never be
 * returned to callers in 5xx responses.
 */
@Tag("unit")
class GlobalExceptionHandlerTest {

    /** Minimal controller that throws controlled exceptions for testing purposes only. */
    @RestController
    @RequestMapping("/test-exc")
    static class ThrowingController {

        @GetMapping("/internal")
        public String throwInternal() {
            throw new RuntimeException("DB host=prod.internal password=hunter2");
        }

        @GetMapping("/validation")
        public String throwValidation() {
            throw new RuntimeException("Validation Error: field 'email' has invalid format");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // Guarantees: unhandled RuntimeExceptions return HTTP 500 with the generic message, not the real cause
    @Test
    void runtimeException_returns500_withGenericMessage() throws Exception {
        mockMvc.perform(get("/test-exc/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred. Please try again later."));
    }

    // Guarantees: the real internal exception message is never present in the 500 response body
    @Test
    void runtimeException_doesNotExposeSensitiveDetailsInBody() throws Exception {
        String body = mockMvc.perform(get("/test-exc/internal"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("prod.internal")
                .doesNotContain("hunter2");
    }

    // Guarantees: validation errors (400) still carry the real descriptive message for the client
    @Test
    void validationError_returns400_withRealMessage() throws Exception {
        mockMvc.perform(get("/test-exc/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Validation Error: field 'email' has invalid format"));
    }

    // Guarantees: 500 response body contains the "status" field set to 500
    @Test
    void runtimeException_returns500_withCorrectStatusField() throws Exception {
        mockMvc.perform(get("/test-exc/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }
}
