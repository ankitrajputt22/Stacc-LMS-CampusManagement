package com.stacc.backend.search;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** What the search API sends: grouped previews, and one kind at a time page by page. */
public final class SearchResponses {

    private SearchResponses() {
    }

    /** One kind of result in the preview: at most {@link GlobalSearchService#PREVIEW_LIMIT} items and the total. */
    public record SearchSection(
            SearchResultType type,
            @Schema(example = "Assignments") String label,
            @Schema(description = "How many results of this kind the signed-in account may see. Never counts anything else.")
            long totalCount,
            List<SearchResult> items) {
    }

    /** The preview: the kinds with at least one result, in a fixed order. Empty kinds are left out. */
    public record GlobalSearchResponse(
            @Schema(description = "The search text after trimming and collapsing spaces.", example = "data") String query,
            List<SearchSection> sections) {
    }

    /** One page of one kind. A kind the account cannot search is simply an empty page. */
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
