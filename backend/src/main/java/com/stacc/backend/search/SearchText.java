package com.stacc.backend.search;

import java.util.Locale;

import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A validated search text and the patterns the queries use. Whitespace is trimmed and collapsed; at least 2 and at
 * most 100 characters remain, and at least 2 of them are not spaces. Matching is case-insensitive: the queries compare
 * lower-cased columns with lower-cased patterns, so it never depends on a column's collation.
 *
 * <p>The text is always a bound parameter, never part of a query string. In patterns it is literal: {@code %},
 * {@code _}, and the escape character {@code !} are escaped, so {@code 50%} finds "50%" and {@code _} finds an
 * underscore, never every row.
 *
 * @param text the normalized text, as shown back to the user
 * @param exact the lower-cased text, for exact matches
 * @param prefix a {@code LIKE} pattern for values that start with the text, escaped with {@code !}
 * @param contains a {@code LIKE} pattern for values that contain the text, escaped with {@code !}
 */
public record SearchText(String text, String exact, String prefix, String contains) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;
    public static final char ESCAPE = '!';

    static final String TOO_SHORT = "Enter at least 2 characters to search.";
    static final String TOO_LONG = "Search text must be at most 100 characters.";

    public static SearchText of(String raw) {
        String text = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (text.replace(" ", "").length() < MIN_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, TOO_SHORT);
        }
        if (text.length() > MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, TOO_LONG);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String escaped = escape(lower);
        return new SearchText(text, lower, escaped + "%", "%" + escaped + "%");
    }

    /** Makes every character literal inside a {@code LIKE ... ESCAPE '!'} pattern. */
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                out.append(ESCAPE);
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Whether a value contains the text, ignoring case: the in-memory twin of the {@code contains} pattern. */
    public boolean matches(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(exact);
    }

    /**
     * The match rank of a value: 0 for an exact match, 1 when it starts with the text, 2 when it only contains it, 3
     * otherwise. The in-memory twin of the queries' ranking.
     */
    public int rank(String value) {
        if (value == null) {
            return 3;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals(exact) ? 0 : lower.startsWith(exact) ? 1 : lower.contains(exact) ? 2 : 3;
    }

    /** The whole number in the text, such as 3 in "3" or "semester 3", or null. */
    public Integer number() {
        String digits = exact.replaceFirst("^(semester|sem)\\s*", "");
        return digits.matches("\\d{1,4}") ? Integer.valueOf(digits) : null;
    }
}
