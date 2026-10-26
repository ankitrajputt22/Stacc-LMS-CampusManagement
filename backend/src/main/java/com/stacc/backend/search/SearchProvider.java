package com.stacc.backend.search;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Answers the search for some kinds of results, through its domain's own access rules. A provider only reads. When the
 * account may not search a kind (it lacks the role or permission the domain's own routes need), it answers empty
 * rather than refusing, so one missing section never fails the whole search.
 */
public interface SearchProvider {

    /** The kinds this provider answers. */
    Set<SearchResultType> types();

    /**
     * One page of results of this kind that the account may see, or empty when it may not search the kind at all. The
     * total of the page counts exactly what the account may see.
     */
    Optional<Page<SearchResult>> search(SearchResultType type, SearchContext context, Pageable page);
}
