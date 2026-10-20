package com.storyrail.library.apitests.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates response bodies against the schemas in contract/openapi.yaml, the pinned copy of the backend's
 * contract (CI fails when the copy differs from library-backend). Nothing here is hand-copied from the contract.
 *
 * <p>Supports the OpenAPI 3.0 subset the contract uses: $ref, type, required, properties,
 * additionalProperties, enum, minLength, maxLength, pattern, minimum, items, and the uuid / date-time formats.
 */
public final class Contract {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final ObjectNode SCHEMAS = loadSchemas();

    private Contract() {
    }

    /** Returns the list of contract violations; empty when the body matches the named schema. */
    public static List<String> violations(String schemaName, JsonNode body) {
        List<String> errors = new ArrayList<>();
        validate(schema(schemaName), body, schemaName, errors);
        return errors;
    }

    /** The genres the contract allows, in contract order. */
    public static List<String> genres() {
        List<String> genres = new ArrayList<>();
        schema("Genre").path("enum").forEach(value -> genres.add(value.asText()));
        return genres;
    }

    private static ObjectNode loadSchemas() {
        Path path = Path.of(System.getProperty("contract.path", "contract/openapi.yaml"));
        try {
            ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
            JsonNode doc = yaml.readTree(Files.readString(path));
            ObjectNode schemas = doc.path("components").path("schemas").deepCopy();
            flattenBook(schemas);
            return schemas;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the API contract at " + path.toAbsolutePath(), e);
        }
    }

    /**
     * Book is allOf [BookInput, {id, available, createdAt}], but BookInput has additionalProperties: false,
     * which would literally reject id. For responses we flatten both parts into one closed object:
     * every documented field, nothing extra.
     */
    private static void flattenBook(ObjectNode schemas) {
        JsonNode input = schemas.path("BookInput");
        JsonNode extras = null;
        for (JsonNode part : schemas.path("Book").path("allOf")) {
            if (!part.has("$ref")) {
                extras = part;
            }
        }
        if (extras == null) {
            throw new IllegalStateException("Unexpected Book schema shape in openapi.yaml");
        }
        ObjectNode book = Json.MAPPER.createObjectNode();
        book.put("type", "object");
        book.put("additionalProperties", false);
        ArrayNode required = book.putArray("required");
        input.path("required").forEach(required::add);
        extras.path("required").forEach(required::add);
        ObjectNode properties = book.putObject("properties");
        properties.setAll((ObjectNode) input.path("properties"));
        properties.setAll((ObjectNode) extras.path("properties"));
        schemas.set("Book", book);
    }

    private static JsonNode schema(String name) {
        JsonNode schema = SCHEMAS.get(name);
        if (schema == null) {
            throw new IllegalArgumentException("Schema " + name + " is missing from openapi.yaml");
        }
        return schema;
    }

    private static JsonNode resolve(JsonNode schema) {
        JsonNode current = schema;
        while (current.has("$ref")) {
            String ref = current.get("$ref").asText();
            current = schema(ref.substring(ref.lastIndexOf('/') + 1));
        }
        return current;
    }

    private static void validate(JsonNode rawSchema, JsonNode value, String at, List<String> errors) {
        JsonNode schema = resolve(rawSchema);
        if (value == null || value.isMissingNode()) {
            errors.add(at + ": missing");
            return;
        }
        if (!hasType(schema, value, at, errors)) {
            return;
        }
        checkEnum(schema, value, at, errors);
        if (value.isTextual()) {
            checkString(schema, value.asText(), at, errors);
        }
        if (value.isNumber() && schema.has("minimum") && value.asDouble() < schema.get("minimum").asDouble()) {
            errors.add(at + ": below minimum " + schema.get("minimum").asText());
        }
        if (value.isArray() && schema.has("items")) {
            for (int i = 0; i < value.size(); i++) {
                validate(schema.get("items"), value.get(i), at + "[" + i + "]", errors);
            }
        }
        if (value.isObject()) {
            checkObject(schema, value, at, errors);
        }
    }

    private static boolean hasType(JsonNode schema, JsonNode value, String at, List<String> errors) {
        if (!schema.has("type")) {
            return true;
        }
        String type = schema.get("type").asText();
        boolean ok = switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            default -> true;
        };
        if (!ok) {
            errors.add(at + ": expected " + type + " but was " + value.getNodeType());
        }
        return ok;
    }

    private static void checkEnum(JsonNode schema, JsonNode value, String at, List<String> errors) {
        if (!schema.has("enum")) {
            return;
        }
        for (JsonNode allowed : schema.get("enum")) {
            if (allowed.equals(value)) {
                return;
            }
        }
        errors.add(at + ": " + value + " is not one of " + schema.get("enum"));
    }

    private static void checkString(JsonNode schema, String text, String at, List<String> errors) {
        int length = text.codePointCount(0, text.length());
        if (schema.has("minLength") && length < schema.get("minLength").asInt()) {
            errors.add(at + ": shorter than " + schema.get("minLength").asInt());
        }
        if (schema.has("maxLength") && length > schema.get("maxLength").asInt()) {
            errors.add(at + ": longer than " + schema.get("maxLength").asInt());
        }
        if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").asText()).matcher(text).find()) {
            errors.add(at + ": does not match " + schema.get("pattern").asText());
        }
        String format = schema.path("format").asText("");
        if ("uuid".equals(format) && !UUID_FORMAT.matcher(text).matches()) {
            errors.add(at + ": not a uuid");
        }
        if ("date-time".equals(format)) {
            try {
                OffsetDateTime.parse(text);
            } catch (DateTimeParseException e) {
                errors.add(at + ": not a date-time");
            }
        }
    }

    private static void checkObject(JsonNode schema, JsonNode value, String at, List<String> errors) {
        for (JsonNode field : schema.path("required")) {
            if (!value.has(field.asText())) {
                errors.add(at + ": required field " + field.asText() + " is missing");
            }
        }
        JsonNode properties = schema.path("properties");
        JsonNode additional = schema.get("additionalProperties");
        for (Map.Entry<String, JsonNode> field : value.properties()) {
            String path = at + "." + field.getKey();
            if (properties.has(field.getKey())) {
                validate(properties.get(field.getKey()), field.getValue(), path, errors);
            } else if (additional != null && additional.isBoolean() && !additional.asBoolean()) {
                errors.add(path + ": not allowed by the contract");
            } else if (additional != null && additional.isObject()) {
                validate(additional, field.getValue(), path, errors);
            }
        }
    }
}
