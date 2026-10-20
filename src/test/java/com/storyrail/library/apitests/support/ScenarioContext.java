package com.storyrail.library.apitests.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * State of one scenario, shared by the step classes through constructor injection (PicoContainer creates a new
 * instance per scenario). Holds the scenario's unique token, the books it created, and the last request/response.
 */
public final class ScenarioContext {

    private final String token = TestData.uniqueToken();
    private final Map<String, JsonNode> books = new LinkedHashMap<>();
    private final Set<String> createdIds = new LinkedHashSet<>();
    private Response response;
    private String responseText = "";
    private JsonNode lastSentBody;

    public String token() {
        return token;
    }

    public void remember(String name, JsonNode book) {
        books.put(name, book.deepCopy());
    }

    public JsonNode book(String name) {
        JsonNode book = books.get(name);
        if (book == null) {
            throw new IllegalArgumentException("No book named \"" + name + "\" in this scenario. Known: " + books.keySet());
        }
        return book;
    }

    public void trackCreated(String id) {
        createdIds.add(id);
    }

    public List<String> createdIds() {
        return new ArrayList<>(createdIds);
    }

    public void recordResponse(Response newResponse) {
        this.response = newResponse;
        this.responseText = newResponse.asString();
    }

    public Response response() {
        if (response == null) {
            throw new IllegalStateException("No request has been sent in this scenario yet");
        }
        return response;
    }

    public String responseText() {
        return responseText;
    }

    /** The response body as JSON; fails the scenario if it is not JSON. */
    public JsonNode responseJson() {
        return Json.parse(responseText);
    }

    public void recordSentBody(JsonNode body) {
        this.lastSentBody = body;
    }

    public JsonNode lastSentBody() {
        if (lastSentBody == null) {
            throw new IllegalStateException("No JSON body has been sent in this scenario yet");
        }
        return lastSentBody;
    }
}
