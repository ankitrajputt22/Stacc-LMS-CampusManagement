package com.stacc.backend.search;

import java.util.Locale;

import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

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

    public boolean matches(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(exact);
    }

    public int rank(String value) {
        if (value == null) {
            return 3;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals(exact) ? 0 : lower.startsWith(exact) ? 1 : lower.contains(exact) ? 2 : 3;
    }

    public Integer number() {
        String digits = exact.replaceFirst("^(semester|sem)\\s*", "");
        return digits.matches("\\d{1,4}") ? Integer.valueOf(digits) : null;
    }
}
