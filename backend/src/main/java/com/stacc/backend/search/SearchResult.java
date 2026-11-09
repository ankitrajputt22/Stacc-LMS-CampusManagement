package com.stacc.backend.search;

import java.time.Instant;
import java.util.regex.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One search result, in plain text: what it is, how to recognize it, and where it opens. It carries no entity, no
 * account ID, and no permission. The action path only navigates; the page it opens checks access again.
 */
public record SearchResult(
        SearchResultType type,
        @Schema(description = "Plain text.", example = "Linked list implementation") String title,
        @Schema(nullable = true, description = "Plain text context, such as the course.", example = "CS301 · Data Structures")
        String subtitle,
        @Schema(nullable = true, description = "At most about 200 characters of plain text from the record.")
        String snippet,
        @Schema(description = "An internal Stacc route, always starting with one slash.", example = "/courses/42/assignments/7")
        String actionPath,
        @Schema(nullable = true, description = "A moment that helps place the result, such as a due date or publication.")
        Instant occurredAt) {

    // One leading slash, then no second slash at the start, no backslash, no whitespace, and no scheme.
    private static final Pattern INTERNAL_PATH = Pattern.compile("^/(?![/\\\\])[^\\s\\\\]*$");

    public SearchResult {
        if (!isInternalPath(actionPath)) {
            throw new IllegalArgumentException("A search result needs an internal action path");
        }
    }

    public static boolean isInternalPath(String path) {
        return path != null && INTERNAL_PATH.matcher(path).matches() && !path.contains("://");
    }
}
