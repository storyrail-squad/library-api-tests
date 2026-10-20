package com.storyrail.library.apitests.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.SecureRandom;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.UUID;

/** Fresh, unique test data, so scenarios never depend on each other or on seed data. */
public final class TestData {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestData() {
    }

    /** Short lowercase token that will not appear in seed data; isolates search results per scenario. */
    public static String uniqueToken() {
        return "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** ISBN-13 check digit: weights 1,3,1,3,… over the first 12 digits. */
    public static int checkDigit(String first12) {
        int sum = 0;
        for (int i = 0; i < first12.length(); i++) {
            int digit = first12.charAt(i) - '0';
            sum += i % 2 == 0 ? digit : digit * 3;
        }
        return (10 - sum % 10) % 10;
    }

    /** A fresh, valid ISBN-13: 978 + 9 random digits + check digit. */
    public static String uniqueIsbn() {
        StringBuilder first12 = new StringBuilder("978");
        for (int i = 0; i < 9; i++) {
            first12.append(RANDOM.nextInt(10));
        }
        return first12.toString() + checkDigit(first12.toString());
    }

    public static String uniqueIsbnEndingWith(int digit) {
        String isbn = uniqueIsbn();
        while (isbn.charAt(12) - '0' != digit) {
            isbn = uniqueIsbn();
        }
        return isbn;
    }

    /** Same first 12 digits as a valid ISBN, wrong check digit. */
    public static String isbnWithBadChecksum() {
        String valid = uniqueIsbn();
        int wrong = (valid.charAt(12) - '0' + 1) % 10;
        return valid.substring(0, 12) + wrong;
    }

    /** The backend validates publishedYear against the current year in UTC, so the tests do too. */
    public static int currentYear() {
        return Year.now(ZoneOffset.UTC).getValue();
    }

    /** A valid BookInput with a unique title and ISBN. */
    public static ObjectNode validBook() {
        ObjectNode book = Json.MAPPER.createObjectNode();
        book.put("title", "API test book " + uniqueToken());
        book.put("author", "Test Author");
        book.put("isbn", uniqueIsbn());
        book.put("publishedYear", 2001);
        book.put("genre", "FICTION");
        return book;
    }
}
