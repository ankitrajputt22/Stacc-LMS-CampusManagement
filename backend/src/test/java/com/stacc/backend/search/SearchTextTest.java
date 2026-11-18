package com.stacc.backend.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SearchTextTest {

    private static void assertRefused(String raw, String message) {
        assertThatThrownBy(() -> SearchText.of(raw)).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    @Test
    void blankAndOneCharacterTextsAreRefused() {
        for (String raw : new String[] {null, "", "   ", "a", " a ", "\t\n"}) {
            assertRefused(raw, SearchText.TOO_SHORT);
        }
    }

    @Test
    void twoToOneHundredCharactersWork() {
        assertThat(SearchText.of("ab").text()).isEqualTo("ab");
        assertThat(SearchText.of("x".repeat(100)).text()).hasSize(100);
        assertRefused("x".repeat(101), SearchText.TOO_LONG);
        assertRefused("a  ", SearchText.TOO_SHORT);
        assertThat(SearchText.of("a b").text()).isEqualTo("a b");
    }

    @Test
    void whitespaceIsTrimmedAndCollapsedBeforeTheLengthCheck() {
        SearchText text = SearchText.of("   Data \t\n  Structures  ");
        assertThat(text.text()).isEqualTo("Data Structures");
        assertThat(text.exact()).isEqualTo("data structures");
        assertThat(SearchText.of(" ".repeat(50) + "ab" + " ".repeat(60)).text()).isEqualTo("ab");
    }

    @Test
    void patternsAreLowerCaseAndWildcardsAreLiteral() {
        SearchText text = SearchText.of("CS%_1!x");
        assertThat(text.exact()).isEqualTo("cs%_1!x");
        assertThat(text.contains()).isEqualTo("%cs!%!_1!!x%");
        assertThat(text.prefix()).isEqualTo("cs!%!_1!!x%");
        assertThat(SearchText.of("%%").contains()).isEqualTo("%!%!%%");
        assertThat(SearchText.of("__").prefix()).isEqualTo("!_!_%");
    }

    @Test
    void theInMemoryMatchAndRankAgreeWithThePatterns() {
        SearchText text = SearchText.of("Data");
        assertThat(text.matches("Big DATA")).isTrue();
        assertThat(text.matches("Networks")).isFalse();
        assertThat(text.matches(null)).isFalse();
        assertThat(text.rank("data")).isZero();
        assertThat(text.rank("Data Structures")).isEqualTo(1);
        assertThat(text.rank("Big Data")).isEqualTo(2);
        assertThat(text.rank("Networks")).isEqualTo(3);
        assertThat(SearchText.of("%%").matches("Data")).isFalse();
    }

    @Test
    void aNumberIsReadForSemesterSearch() {
        assertThat(SearchText.of("12").number()).isEqualTo(12);
        assertThat(SearchText.of("Semester 3").number()).isEqualTo(3);
        assertThat(SearchText.of("sem3").number()).isEqualTo(3);
        assertThat(SearchText.of("CS301").number()).isNull();
    }
}
