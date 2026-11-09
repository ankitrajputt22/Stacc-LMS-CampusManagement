package com.stacc.backend.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SearchResultTest {

    @Test
    void actionPathsAreInternalRoutesOnly() {
        for (String path : new String[] {"/courses/4", "/calendar?month=2026-11", "/hostel?application=7", "/"}) {
            assertThat(SearchResult.isInternalPath(path)).as(path).isTrue();
        }
        for (String path : new String[] {null, "", "courses/4", "//evil.example", "/\\evil", "http://evil.example",
                "https://x", "/a b", "javascript:alert(1)", "/x?next=https://evil.example"}) {
            assertThat(SearchResult.isInternalPath(path)).as(String.valueOf(path)).isFalse();
            assertThatThrownBy(() -> new SearchResult(SearchResultType.COURSE, "T", null, null, path, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void theResultCarriesNoIdsOrPermissions() {
        assertThat(SearchResult.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("type", "title", "subtitle", "snippet", "actionPath", "occurredAt");
    }

    @Test
    void snippetsAreShortPlainTextOnOneLine() {
        assertThat(SearchFormat.snippet(null)).isNull();
        assertThat(SearchFormat.snippet("  ")).isNull();
        assertThat(SearchFormat.snippet("Line one\n\nline   two")).isEqualTo("Line one line two");
        String longText = "word ".repeat(100);
        String snippet = SearchFormat.snippet(longText);
        assertThat(snippet).endsWith("…").hasSizeLessThanOrEqualTo(SearchFormat.SNIPPET_LENGTH + 1);
        assertThat(SearchFormat.snippet("<b>bold</b>")).isEqualTo("<b>bold</b>");
    }

    @Test
    void textHelpers() {
        assertThat(SearchFormat.join("A", null, " ", "B")).isEqualTo("A · B");
        assertThat(SearchFormat.join((String) null)).isNull();
        assertThat(SearchFormat.words(SearchResultType.TEACHING_COURSE)).isEqualTo("Teaching course");
        assertThat(SearchFormat.days(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3))).isEqualTo("3 Nov 2026");
        assertThat(SearchFormat.days(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5))).isEqualTo("3 Nov 2026 – 5 Nov 2026");
    }
}
