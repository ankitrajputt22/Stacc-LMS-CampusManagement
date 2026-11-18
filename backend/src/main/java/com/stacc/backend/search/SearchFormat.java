package com.stacc.backend.search;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SearchFormat {

    public static final int SNIPPET_LENGTH = 200;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private SearchFormat() {
    }

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

    public static String join(String... parts) {
        String joined = Arrays.stream(parts).filter(Objects::nonNull).filter(part -> !part.isBlank())
                .collect(Collectors.joining(" · "));
        return joined.isEmpty() ? null : joined;
    }

    public static String words(Enum<?> status) {
        if (status == null) {
            return null;
        }
        String lower = status.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static String days(LocalDate start, LocalDate end) {
        return start.equals(end) ? DAY.format(start) : DAY.format(start) + " – " + DAY.format(end);
    }
}
