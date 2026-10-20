package com.storyrail.library.apitests.support;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands ${…} placeholders in feature text, so scenarios stay readable and still use fresh data.
 *
 * <ul>
 *   <li>${token}, ${tokenUpper}, ${tokenPart}: this scenario's unique search token (and variants)</li>
 *   <li>${isbn}, ${isbnEndingWith0}, ${badChecksumIsbn}, ${isbn12}, ${isbn14}: generated ISBNs</li>
 *   <li>${currentYear}, ${nextYear}: publication years around the UTC current year</li>
 *   <li>${uuid}: a valid UUID that no book has</li>
 *   <li>${repeat:X:200}: the character X repeated 200 times</li>
 *   <li>${id:name}, ${isbn:name}, ${title:name}: a field of a book created earlier in the scenario</li>
 *   <li>${sent:field}: a field of the last JSON body sent</li>
 * </ul>
 */
public final class Placeholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private Placeholders() {
    }

    public static String expand(String text, ScenarioContext context) {
        if (text == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(value(matcher.group(1), context)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String value(String key, ScenarioContext context) {
        String[] parts = key.split(":", 3);
        return switch (parts[0]) {
            case "token" -> context.token();
            case "tokenUpper" -> context.token().toUpperCase(java.util.Locale.ROOT);
            case "tokenPart" -> context.token().substring(1, 8);
            case "isbn" -> parts.length == 1 ? TestData.uniqueIsbn() : context.book(parts[1]).path("isbn").asText();
            case "isbnEndingWith0" -> TestData.uniqueIsbnEndingWith(0);
            case "badChecksumIsbn" -> TestData.isbnWithBadChecksum();
            case "isbn12" -> TestData.uniqueIsbn().substring(0, 12);
            case "isbn14" -> TestData.uniqueIsbn() + "0";
            case "currentYear" -> String.valueOf(TestData.currentYear());
            case "nextYear" -> String.valueOf(TestData.currentYear() + 1);
            case "uuid" -> UUID.randomUUID().toString();
            case "repeat" -> parts[1].repeat(Integer.parseInt(parts[2]));
            case "id" -> context.book(parts[1]).path("id").asText();
            case "title" -> context.book(parts[1]).path("title").asText();
            case "sent" -> context.lastSentBody().path(parts[1]).asText();
            default -> throw new IllegalArgumentException("Unknown placeholder ${" + key + "}");
        };
    }
}
