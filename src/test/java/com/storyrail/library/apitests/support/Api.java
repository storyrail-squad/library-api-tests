package com.storyrail.library.apitests.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Thin HTTP client over RestAssured. Records every response in the scenario context. */
public final class Api {

    public static final String BOOKS = "/api/books";

    private final ScenarioContext context;

    public Api(ScenarioContext context) {
        this.context = context;
    }

    /** Sends a request to a path that may carry a query string, e.g. "/api/books?q=x&size=2". */
    public Response send(String method, String pathAndQuery, String body) {
        String path = pathAndQuery;
        Map<String, String> query = new LinkedHashMap<>();
        int q = pathAndQuery.indexOf('?');
        if (q >= 0) {
            path = pathAndQuery.substring(0, q);
            for (String pair : pathAndQuery.substring(q + 1).split("&")) {
                if (!pair.isEmpty()) {
                    int eq = pair.indexOf('=');
                    query.put(eq < 0 ? pair : pair.substring(0, eq), eq < 0 ? "" : pair.substring(eq + 1));
                }
            }
        }
        RequestSpecification request = base().queryParams(query);
        return execute(request, method, path, body);
    }

    /** Sends a request to /api/books/{id}[suffix] with the id URL-encoded exactly once (ids may be malformed on purpose). */
    public Response sendToBookId(String method, String rawId, String suffix, String body) {
        String encoded = URLEncoder.encode(rawId, StandardCharsets.UTF_8).replace("+", "%20");
        RequestSpecification request = base().urlEncodingEnabled(false);
        return execute(request, method, BOOKS + "/" + encoded + suffix, body);
    }

    /** Deletes a book without recording the response (used by the cleanup hook). */
    public int deleteQuietly(String id) {
        return base().delete(BOOKS + "/" + id).statusCode();
    }

    private RequestSpecification base() {
        return RestAssured.given().baseUri(ApiConfig.baseUrl()).accept("application/json, application/problem+json");
    }

    private Response execute(RequestSpecification request, String method, String path, String body) {
        RequestSpecification spec = request;
        if (body != null) {
            spec = spec.contentType("application/json").body(body);
            JsonNode parsed = Json.parseOrNull(body);
            if (parsed != null) {
                context.recordSentBody(parsed);
            }
        }
        Response response = spec.request(method.toUpperCase(Locale.ROOT), path);
        context.recordResponse(response);
        if ("POST".equalsIgnoreCase(method) && BOOKS.equals(path) && response.statusCode() == 201) {
            JsonNode created = Json.parseOrNull(response.asString());
            if (created != null && created.hasNonNull("id")) {
                context.trackCreated(created.get("id").asText());
            }
        }
        return response;
    }
}
