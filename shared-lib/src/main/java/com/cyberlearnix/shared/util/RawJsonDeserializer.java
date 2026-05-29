package com.cyberlearnix.shared.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Deserializes any JSON value (array, object, string, null) into its raw JSON string representation.
 * Used for JSONB columns so Hibernate always receives a String.
 */
public class RawJsonDeserializer extends JsonDeserializer<String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = MAPPER.readTree(p);
        if (node == null || node.isNull()) {
            return null;
        }
        // If the incoming value is already a plain string (e.g. "[]"), return it as-is
        if (node.isTextual()) {
            return node.asText();
        }
        // Otherwise (array, object, number, boolean) → serialize to JSON string
        return MAPPER.writeValueAsString(node);
    }
}
