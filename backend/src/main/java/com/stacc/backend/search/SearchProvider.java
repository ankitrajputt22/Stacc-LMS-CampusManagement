package com.stacc.backend.search;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchProvider {

    Set<SearchResultType> types();

    Optional<Page<SearchResult>> search(SearchResultType type, SearchContext context, Pageable page);
}
