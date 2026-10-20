package com.storyrail.library.apitests.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** One shared Jackson mapper and small helpers. */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    /** Parses JSON text; returns null when the text is not JSON (e.g. a deliberately broken body). */
    public static JsonNode parseOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static JsonNode parse(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not valid JSON: " + text, e);
        }
    }

    public static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise JSON", e);
        }
    }
}
