package com.stacc.backend.search;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** Small plain-text helpers for result text. Nothing here is HTML: the frontend shows every string as text. */
public final class SearchFormat {

    public static final int SNIPPET_LENGTH = 200;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private SearchFormat() {
    }

    /** The start of a text on one line, at most about 200 characters, cut at a word with an ellipsis. */
    public static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String line = text.strip().replaceAll("\\s+", " ");
        if (line.length() <= SNIPPET_LENGTH) {
            return line;
        }
        String cut = line.substring(0, SNIPPET_LENGTH);
        int space = cut.lastIndexOf(' ');
        return (space > SNIPPET_LENGTH / 2 ? cut.substring(0, space) : cut).stripTrailing() + "…";
    }

    /** The non-empty parts joined with " · ". */
    public static String join(String... parts) {
        String joined = Arrays.stream(parts).filter(Objects::nonNull).filter(part -> !part.isBlank())
                .collect(Collectors.joining(" · "));
        return joined.isEmpty() ? null : joined;
    }

    /** A status code in words, such as IN_PROGRESS to "In progress". */
    public static String words(Enum<?> status) {
        if (status == null) {
            return null;
        }
        String lower = status.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** A date range such as "3 Nov 2026" or "3 Nov 2026 – 5 Nov 2026". */
    public static String days(LocalDate start, LocalDate end) {
        return start.equals(end) ? DAY.format(start) : DAY.format(start) + " – " + DAY.format(end);
    }
}
