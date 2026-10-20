package com.storyrail.library.apitests.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.storyrail.library.apitests.support.Api;
import com.storyrail.library.apitests.support.Json;
import com.storyrail.library.apitests.support.Placeholders;
import com.storyrail.library.apitests.support.ScenarioContext;
import com.storyrail.library.apitests.support.TestData;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Map;

/** Steps that set up books and call the Books API with readable, domain-level wording. */
public final class BookSteps {

    private static final String OMIT = "(omitted)";

    private final ScenarioContext context;
    private final Api api;

    public BookSteps(ScenarioContext context, Api api) {
        this.context = context;
        this.api = api;
    }

    // ---------- Given ----------

    @Given("a book {string} exists")
    public void aBookExists(String name) {
        createAndRemember(name, TestData.validBook());
    }

    /** Columns (all optional): name, title, author, isbn, publishedYear, genre. Plain values; ${…} placeholders allowed. */
    @Given("these books exist:")
    public void theseBooksExist(DataTable table) {
        int row = 0;
        for (Map<String, String> columns : table.asMaps()) {
            ObjectNode book = TestData.validBook();
            for (Map.Entry<String, String> column : columns.entrySet()) {
                if (!"name".equals(column.getKey()) && column.getValue() != null) {
                    String value = Placeholders.expand(column.getValue(), context);
                    if ("publishedYear".equals(column.getKey())) {
                        book.put("publishedYear", Integer.parseInt(value));
                    } else {
                        book.put(column.getKey(), value);
                    }
                }
            }
            String name = columns.getOrDefault("name", null);
            createAndRemember(name == null ? "book" + row : name, book);
            row++;
        }
    }

    @Given("book {string} is on loan")
    public void bookIsOnLoan(String name) {
        api.sendToBookId("PATCH", context.book(name).path("id").asText(), "/availability", "{\"available\":false}");
        assertThat(context.response().statusCode()).as(context.responseText()).isEqualTo(200);
        context.remember(name, context.responseJson());
    }

    // ---------- When: list and fetch ----------

    @When("I list books")
    public void iListBooks() {
        api.send("GET", Api.BOOKS, null);
    }

    @When("I list books with {string}")
    public void iListBooksWith(String query) {
        api.send("GET", Api.BOOKS + "?" + Placeholders.expand(query, context), null);
    }

    @When("I fetch book {string}")
    public void iFetchBook(String name) {
        api.sendToBookId("GET", context.book(name).path("id").asText(), "", null);
    }

    @When("I fetch book id {string}")
    public void iFetchBookId(String id) {
        api.sendToBookId("GET", Placeholders.expand(id, context), "", null);
    }

    // ---------- When: any endpoint ----------

    @When("I send {word} {string}")
    public void iSend(String method, String path) {
        api.send(method, Placeholders.expand(path, context), null);
    }

    @When("I send {word} {string} with the body:")
    public void iSendWithTheBody(String method, String path, String body) {
        api.send(method, Placeholders.expand(path, context), Placeholders.expand(body, context));
    }

    // ---------- When: create ----------

    @When("I create a valid book")
    public void iCreateAValidBook() {
        post(TestData.validBook());
    }

    /** Table rows: field | value, where value is a JSON literal ("text", 1999, true) or (omitted). */
    @When("I create a valid book with:")
    public void iCreateAValidBookWith(DataTable overrides) {
        post(withOverrides(TestData.validBook(), overrides));
    }

    @When("I create a valid book without {string}")
    public void iCreateAValidBookWithout(String field) {
        ObjectNode book = TestData.validBook();
        book.remove(field);
        post(book);
    }

    @When("I create a book with the body:")
    public void iCreateABookWithTheBody(String body) {
        api.send("POST", Api.BOOKS, Placeholders.expand(body, context));
    }

    @When("I create a valid book with the ISBN of book {string}")
    public void iCreateAValidBookWithTheIsbnOf(String name) {
        ObjectNode book = TestData.validBook();
        book.put("isbn", context.book(name).path("isbn").asText());
        post(book);
    }

    // ---------- When: replace, availability, delete ----------

    @When("I replace book {string} with a valid book")
    public void iReplaceBookWithAValidBook(String name) {
        put(name, TestData.validBook());
    }

    @When("I replace book {string} with a valid book with:")
    public void iReplaceBookWithAValidBookWith(String name, DataTable overrides) {
        put(name, withOverrides(TestData.validBook(), overrides));
    }

    @When("I replace book {string} with the body:")
    public void iReplaceBookWithTheBody(String name, String body) {
        api.sendToBookId("PUT", context.book(name).path("id").asText(), "", Placeholders.expand(body, context));
    }

    @When("I replace book id {string} with a valid book")
    public void iReplaceBookIdWithAValidBook(String id) {
        api.sendToBookId("PUT", Placeholders.expand(id, context), "", Json.write(TestData.validBook()));
    }

    @When("I set the availability of book {string} to {word}")
    public void iSetTheAvailabilityOfBookTo(String name, String available) {
        sendAvailability(context.book(name).path("id").asText(), "{\"available\":" + available + "}");
    }

    @When("I set the availability of book id {string} to {word}")
    public void iSetTheAvailabilityOfBookIdTo(String id, String available) {
        sendAvailability(Placeholders.expand(id, context), "{\"available\":" + available + "}");
    }

    @When("I send the availability body {string} for book {string}")
    public void iSendTheAvailabilityBody(String body, String name) {
        sendAvailability(context.book(name).path("id").asText(), body);
    }

    @When("I delete book {string}")
    public void iDeleteBook(String name) {
        api.sendToBookId("DELETE", context.book(name).path("id").asText(), "", null);
    }

    @When("I delete book id {string}")
    public void iDeleteBookId(String id) {
        api.sendToBookId("DELETE", Placeholders.expand(id, context), "", null);
    }

    // ---------- helpers ----------

    private void createAndRemember(String name, ObjectNode book) {
        post(book);
        assertThat(context.response().statusCode()).as("creating book %s: %s", name, context.responseText()).isEqualTo(201);
        context.remember(name, context.responseJson());
    }

    private void post(ObjectNode body) {
        api.send("POST", Api.BOOKS, Json.write(body));
    }

    private void put(String name, ObjectNode body) {
        api.sendToBookId("PUT", context.book(name).path("id").asText(), "", Json.write(body));
    }

    private void sendAvailability(String id, String body) {
        api.sendToBookId("PATCH", id, "/availability", Placeholders.expand(body, context));
    }

    private ObjectNode withOverrides(ObjectNode book, DataTable overrides) {
        List<List<String>> rows = overrides.asLists();
        for (List<String> row : rows) {
            String field = row.get(0);
            String raw = row.get(1) == null ? "\"\"" : row.get(1);
            if (OMIT.equals(raw)) {
                book.remove(field);
            } else {
                JsonNode value = Json.parse(Placeholders.expand(raw, context));
                book.set(field, value);
            }
        }
        return book;
    }
}
