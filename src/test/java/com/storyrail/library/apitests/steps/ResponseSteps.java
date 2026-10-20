package com.storyrail.library.apitests.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.storyrail.library.apitests.support.Api;
import com.storyrail.library.apitests.support.Contract;
import com.storyrail.library.apitests.support.Json;
import com.storyrail.library.apitests.support.Placeholders;
import com.storyrail.library.apitests.support.ScenarioContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Assertions on the last response: status, contract, problem details, books and pages. */
public final class ResponseSteps {

    /** BE-AC9: error bodies must not reveal stack traces or internal class names. */
    private static final List<Pattern> LEAKS = List.of(
            Pattern.compile("(?i)trace"),
            Pattern.compile("(?i)exception"),
            Pattern.compile("(?i)java\\."),
            Pattern.compile("\\bat [\\w$.]+\\("),
            Pattern.compile("(?i)springframework"),
            Pattern.compile("(?i)hibernate"),
            Pattern.compile("(?i)jackson"));

    private final ScenarioContext context;
    private final Api api;

    public ResponseSteps(ScenarioContext context, Api api) {
        this.context = context;
        this.api = api;
    }

    // ---------- status and contract ----------

    @Then("the response status is {int}")
    public void theResponseStatusIs(int status) {
        assertThat(context.response().statusCode()).as(context.responseText()).isEqualTo(status);
    }

    @Then("the response is a {word} matching the contract")
    public void theResponseMatchesTheContract(String schema) {
        assertThat(context.response().contentType()).startsWith("application/json");
        assertThat(Contract.violations(schema, context.responseJson())).as("%s vs openapi.yaml", schema).isEmpty();
    }

    @Then("the response body is empty")
    public void theResponseBodyIsEmpty() {
        assertThat(context.responseText()).isEmpty();
    }

    @Then("the JSON field {string} is {string}")
    public void theJsonFieldIs(String field, String expected) {
        assertThat(context.responseJson().path(field).asText()).isEqualTo(expected);
    }

    // ---------- problems (RFC 7807) ----------

    @Then("the response is a problem with status {int}")
    public void theResponseIsAProblem(int status) {
        assertProblem(status, null);
    }

    @Then("the response is a problem with status {int} and title {string}")
    public void theResponseIsAProblemWithTitle(int status, String title) {
        assertProblem(status, title);
    }

    /** BE-AC4: 400 "Validation failed" with a non-empty message for each listed field (comma-separated). */
    @Then("the response is a validation error for {string}")
    public void theResponseIsAValidationErrorFor(String fields) {
        JsonNode problem = assertProblem(400, "Validation failed");
        JsonNode errors = problem.path("errors");
        assertThat(errors.isObject()).as("problem has an errors object: %s", problem).isTrue();
        for (String field : split(fields)) {
            assertThat(errors.has(field)).as("errors should include \"%s\": %s", field, errors).isTrue();
            assertThat(errors.get(field).asText().trim()).as("message for \"%s\"", field).isNotEmpty();
        }
    }

    @Then("the validation errors do not include {string}")
    public void theValidationErrorsDoNotInclude(String field) {
        assertThat(context.responseJson().path("errors").has(field)).as("errors should not include %s", field).isFalse();
    }

    @Then("the problem has an error for {string}")
    public void theProblemHasAnErrorFor(String field) {
        assertThat(context.responseJson().path("errors").path(field).asText()).as("errors.%s", field).isNotBlank();
    }

    @Then("the response does not leak internals")
    public void theResponseDoesNotLeakInternals() {
        assertNoLeak(context.responseText());
    }

    // ---------- books ----------

    @Then("the response book has the fields I sent and is available")
    public void theResponseBookHasTheFieldsISentAndIsAvailable() {
        JsonNode book = context.responseJson();
        for (Map.Entry<String, JsonNode> field : context.lastSentBody().properties()) {
            assertThat(book.get(field.getKey())).as(field.getKey()).isEqualTo(field.getValue());
        }
        assertThat(book.path("available").asBoolean(false)).as("available").isTrue();
    }

    @Then("the Location header points to the created book")
    public void theLocationHeaderPointsToTheCreatedBook() {
        String id = context.responseJson().path("id").asText();
        assertThat(context.response().header("Location")).endsWith(Api.BOOKS + "/" + id);
    }

    @Then("fetching the response book returns the same book")
    public void fetchingTheResponseBookReturnsTheSameBook() {
        JsonNode book = context.responseJson();
        api.sendToBookId("GET", book.path("id").asText(), "", null);
        theResponseStatusIs(200);
        assertThat(context.responseJson()).isEqualTo(book);
    }

    /** Table rows: field | value, where value is a JSON literal. */
    @Then("the response book has:")
    public void theResponseBookHas(DataTable expected) {
        JsonNode book = context.responseJson();
        for (List<String> row : expected.asLists()) {
            JsonNode value = Json.parse(Placeholders.expand(row.get(1), context));
            assertThat(book.get(row.get(0))).as(row.get(0)).isEqualTo(value);
        }
    }

    @Then("the response book equals book {string}")
    public void theResponseBookEqualsBook(String name) {
        assertThat(context.responseJson()).isEqualTo(context.book(name));
    }

    @Then("the response book equals book {string} with available {word}")
    public void theResponseBookEqualsBookWithAvailable(String name, String available) {
        ObjectNode expected = context.book(name).deepCopy();
        expected.put("available", Boolean.parseBoolean(available));
        assertThat(context.responseJson()).isEqualTo(expected);
    }

    @Then("the response book replaces book {string} with the fields I sent")
    public void theResponseBookReplacesBook(String name) {
        JsonNode original = context.book(name);
        ObjectNode expected = context.lastSentBody().deepCopy();
        expected.set("id", original.get("id"));
        expected.set("createdAt", original.get("createdAt"));
        expected.set("available", original.get("available"));
        assertThat(context.responseJson()).isEqualTo(expected);
    }

    @Then("the response book is remembered as {string}")
    public void theResponseBookIsRememberedAs(String name) {
        context.remember(name, context.responseJson());
    }

    @Then("the response book is not book {string}")
    public void theResponseBookIsNotBook(String name) {
        assertThat(context.responseJson().path("id").asText()).isNotEqualTo(context.book(name).path("id").asText());
    }

    @Then("book {string} is unchanged")
    public void bookIsUnchanged(String name) {
        api.sendToBookId("GET", context.book(name).path("id").asText(), "", null);
        theResponseStatusIs(200);
        assertThat(context.responseJson()).isEqualTo(context.book(name));
    }

    @Then("book {string} is not found")
    public void bookIsNotFound(String name) {
        api.sendToBookId("GET", context.book(name).path("id").asText(), "", null);
        assertProblem(404, "Book not found");
    }

    // ---------- pages ----------

    @Then("the page has page {int} and size {int} and consistent totals")
    public void thePageHasConsistentTotals(int page, int size) {
        JsonNode body = context.responseJson();
        assertThat(body.path("page").asInt()).isEqualTo(page);
        assertThat(body.path("size").asInt()).isEqualTo(size);
        assertThat(body.path("items").size()).isLessThanOrEqualTo(size);
        long totalItems = body.path("totalItems").asLong();
        assertThat(body.path("totalPages").asLong()).isEqualTo((totalItems + size - 1) / size);
    }

    /** Table rows: page | size | totalItems | totalPages (any subset), as "name | number". */
    @Then("the page shows:")
    public void thePageShows(DataTable expected) {
        JsonNode body = context.responseJson();
        for (Map.Entry<String, String> row : expected.asMap().entrySet()) {
            assertThat(body.path(row.getKey()).asLong()).as(row.getKey()).isEqualTo(Long.parseLong(row.getValue()));
        }
    }

    @Then("the page has no items")
    public void thePageHasNoItems() {
        assertThat(context.responseJson().path("items").size()).isZero();
    }

    @Then("the page has at least {int} item(s)")
    public void thePageHasAtLeastItems(int count) {
        assertThat(context.responseJson().path("items").size()).isGreaterThanOrEqualTo(count);
        assertThat(context.responseJson().path("totalItems").asLong()).isGreaterThanOrEqualTo(count);
    }

    @Then("the listed titles are:")
    public void theListedTitlesAre(List<String> titles) {
        List<String> expected = titles.stream().map(t -> Placeholders.expand(t, context)).toList();
        assertThat(listedTitles()).containsExactlyElementsOf(expected);
    }

    @Then("the listed titles are sorted ignoring case")
    public void theListedTitlesAreSortedIgnoringCase() {
        List<String> titles = listedTitles().stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
        for (int i = 1; i < titles.size(); i++) {
            assertThat(titles.get(i - 1).compareTo(titles.get(i)))
                    .as("\"%s\" should sort before \"%s\"", titles.get(i - 1), titles.get(i))
                    .isLessThanOrEqualTo(0);
        }
    }

    @Then("the listed books are exactly {string}")
    public void theListedBooksAreExactly(String names) {
        List<String> expected = split(names).stream().map(n -> context.book(n).path("id").asText()).toList();
        assertThat(listedIds()).containsExactlyElementsOf(expected);
    }

    @Then("the list contains book {string}")
    public void theListContainsBook(String name) {
        assertThat(listedIds()).contains(context.book(name).path("id").asText());
    }

    @Then("every listed book has {word} {word}")
    public void everyListedBookHas(String field, String value) {
        for (JsonNode book : context.responseJson().path("items")) {
            assertThat(book.path(field).asText()).as("%s of %s", field, book.path("title").asText()).isEqualTo(value);
        }
    }

    // ---------- helpers ----------

    private JsonNode assertProblem(int status, String title) {
        String text = context.responseText();
        assertThat(context.response().statusCode()).as(text).isEqualTo(status);
        assertThat(context.response().contentType()).startsWith("application/problem+json");
        JsonNode body = Json.parse(text);
        assertThat(Contract.violations("Problem", body)).as("Problem vs openapi.yaml").isEmpty();
        assertThat(body.path("status").asInt()).as("problem status field").isEqualTo(status);
        if (title != null) {
            assertThat(body.path("title").asText()).isEqualTo(title);
        }
        assertNoLeak(text);
        return body;
    }

    private static void assertNoLeak(String text) {
        for (Pattern leak : LEAKS) {
            assertThat(leak.matcher(text).find()).as("error body leaks internals (%s): %s", leak.pattern(), text).isFalse();
        }
    }

    private List<String> listedTitles() {
        List<String> titles = new ArrayList<>();
        context.responseJson().path("items").forEach(b -> titles.add(b.path("title").asText()));
        return titles;
    }

    private List<String> listedIds() {
        List<String> ids = new ArrayList<>();
        context.responseJson().path("items").forEach(b -> ids.add(b.path("id").asText()));
        return ids;
    }

    private static List<String> split(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
