package com.cyberlearnix.enrollment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for {@link FormValidationService}.
 *
 * No Spring context or mocks needed — the service only depends on ObjectMapper,
 * which is constructed directly.
 */
@Tag("unit")
class FormValidationServiceTest {

    private FormValidationService service;

    @BeforeEach
    void setUp() {
        service = new FormValidationService(new ObjectMapper());
    }

    // ── null / empty guards ──────────────────────────────────────────────────────

    @Test
    void validate_doesNothing_whenFieldsJsonIsNull() {
        assertThatCode(() -> service.validateResponse(null, "{\"name\":\"Alice\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_doesNothing_whenFieldsJsonIsEmpty() {
        assertThatCode(() -> service.validateResponse("", "{\"name\":\"Alice\"}"))
                .doesNotThrowAnyException();
    }

    // ── required field check ────────────────────────────────────────────────────

    @Test
    void validate_throws_whenRequiredFieldIsMissing() {
        String fields = "[{\"id\":\"name\",\"label\":\"Full Name\",\"field_type\":\"text\",\"is_required\":true}]";
        String submission = "{\"other\":\"value\"}";

        assertThatThrownBy(() -> service.validateResponse(fields, submission))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("required");
    }

    @Test
    void validate_passes_whenRequiredFieldIsPresent() {
        String fields = "[{\"id\":\"name\",\"label\":\"Full Name\",\"field_type\":\"text\",\"is_required\":true}]";
        String submission = "{\"name\":\"Alice\"}";

        assertThatCode(() -> service.validateResponse(fields, submission))
                .doesNotThrowAnyException();
    }

    // ── email validation ─────────────────────────────────────────────────────────

    @Test
    void validate_throws_whenEmailFieldHasInvalidFormat() {
        String fields = "[{\"id\":\"email\",\"label\":\"Email\",\"field_type\":\"email\",\"is_required\":false}]";
        String submission = "{\"email\":\"not-an-email\"}";

        assertThatThrownBy(() -> service.validateResponse(fields, submission))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid email");
    }

    @Test
    void validate_passes_whenEmailFieldHasValidFormat() {
        String fields = "[{\"id\":\"email\",\"label\":\"Email\",\"field_type\":\"email\",\"is_required\":false}]";
        String submission = "{\"email\":\"user@example.com\"}";

        assertThatCode(() -> service.validateResponse(fields, submission))
                .doesNotThrowAnyException();
    }

    // ── section_header skip ──────────────────────────────────────────────────────

    @Test
    void validate_skips_sectionHeaderEvenIfMarkedRequired() {
        String fields = "[{\"id\":\"hdr\",\"label\":\"Section\",\"field_type\":\"section_header\",\"is_required\":true}]";
        String submission = "{}";

        assertThatCode(() -> service.validateResponse(fields, submission))
                .doesNotThrowAnyException();
    }

    // ── number field check ───────────────────────────────────────────────────────

    @Test
    void validate_throws_whenNumberFieldReceivesNonNumericValue() {
        String fields = "[{\"id\":\"age\",\"label\":\"Age\",\"field_type\":\"number\",\"is_required\":false}]";
        String submission = "{\"age\":\"twenty\"}";

        assertThatThrownBy(() -> service.validateResponse(fields, submission))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("number");
    }
}
