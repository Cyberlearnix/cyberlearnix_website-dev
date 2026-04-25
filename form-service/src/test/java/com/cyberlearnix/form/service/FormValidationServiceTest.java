package com.cyberlearnix.form.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for {@link FormValidationService} (form-service).
 *
 * No Spring context needed — the service only depends on ObjectMapper,
 * which is constructed directly.
 */
@Tag("unit")
class FormValidationServiceTest {

    private FormValidationService service;

    @BeforeEach
    void setUp() {
        service = new FormValidationService(new ObjectMapper());
    }

    @Test
    void validate_doesNothing_whenFieldsJsonIsNull() throws Exception {
        assertThatCode(() -> service.validateResponse(null, "{\"x\":\"1\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_doesNothing_whenFieldsJsonIsEmpty() throws Exception {
        assertThatCode(() -> service.validateResponse("", "{\"x\":\"1\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_throws_whenRequiredFieldIsMissing() {
        String fields = "[{\"id\":\"title\",\"label\":\"Title\",\"field_type\":\"text\",\"is_required\":true}]";
        String data = "{}";

        assertThatThrownBy(() -> service.validateResponse(fields, data))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("required");
    }

    @Test
    void validate_passes_whenRequiredFieldIsPresent() throws Exception {
        String fields = "[{\"id\":\"title\",\"label\":\"Title\",\"field_type\":\"text\",\"is_required\":true}]";
        String data = "{\"title\":\"My Form\"}";

        assertThatCode(() -> service.validateResponse(fields, data))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_throws_whenEmailFieldHasInvalidFormat() {
        String fields = "[{\"id\":\"email\",\"label\":\"Email\",\"field_type\":\"email\",\"is_required\":false}]";
        String data = "{\"email\":\"not-valid\"}";

        assertThatThrownBy(() -> service.validateResponse(fields, data))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid email");
    }

    @Test
    void validate_passes_whenEmailFieldHasValidFormat() throws Exception {
        String fields = "[{\"id\":\"email\",\"label\":\"Email\",\"field_type\":\"email\",\"is_required\":false}]";
        String data = "{\"email\":\"admin@cyberlearnix.com\"}";

        assertThatCode(() -> service.validateResponse(fields, data))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_skips_paragraphAndHtmlFieldTypes() throws Exception {
        String fields = "[" +
                "{\"id\":\"p1\",\"label\":\"Intro\",\"field_type\":\"paragraph\",\"is_required\":true}," +
                "{\"id\":\"h1\",\"label\":\"HTML Block\",\"field_type\":\"html\",\"is_required\":true}" +
                "]";
        String data = "{}";

        assertThatCode(() -> service.validateResponse(fields, data))
                .doesNotThrowAnyException();
    }
}
