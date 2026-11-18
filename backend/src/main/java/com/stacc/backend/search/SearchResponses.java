package com.stacc.backend.search;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class SearchResponses {

    private SearchResponses() {
    }

    public record SearchSection(
            SearchResultType type,
            @Schema(example = "Assignments") String label,
            @Schema(description = "How many results of this kind the signed-in account may see. Never counts anything else.")
            long totalCount,
            List<SearchResult> items) {
    }

    public record GlobalSearchResponse(
            @Schema(description = "The search text after trimming and collapsing spaces.", example = "data") String query,
            List<SearchSection> sections) {
    }

    public record SearchResultPage(
            String query,
            SearchResultType type,
            String label,
            List<SearchResult> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
