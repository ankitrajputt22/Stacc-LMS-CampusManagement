package com.stacc.backend.search;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.search.SearchResponses.GlobalSearchResponse;
import com.stacc.backend.search.SearchResponses.SearchResultPage;
import com.stacc.backend.search.SearchResponses.SearchSection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes a search from its providers. It validates the text, asks each provider for the kinds it answers, and keeps
 * the kinds with results, in the fixed order of {@link SearchResultType}. It owns no access rule: each provider applies
 * its own domain's. It only reads, and stores nothing, not even the query.
 */
@Service
@Transactional(readOnly = true)
public class GlobalSearchService {

    /** At most this many results of each kind in the preview. */
    public static final int PREVIEW_LIMIT = 5;
    public static final int MAX_PAGE_SIZE = 100;

    static final String TYPE_REQUIRED = "Choose a result type.";

    private final Map<SearchResultType, SearchProvider> providers = new EnumMap<>(SearchResultType.class);

    public GlobalSearchService(List<SearchProvider> providers) {
        for (SearchProvider provider : providers) {
            for (SearchResultType type : provider.types()) {
                if (this.providers.putIfAbsent(type, provider) != null) {
                    throw new IllegalStateException("Two search providers answer " + type);
                }
            }
        }
    }

    /** The preview: up to {@link #PREVIEW_LIMIT} results of each kind the actor may search, with each kind's total. */
    public GlobalSearchResponse preview(SearchActor actor, String query) {
        SearchContext context = new SearchContext(actor, SearchText.of(query));
        PageRequest firstPage = PageRequest.of(0, PREVIEW_LIMIT);
        List<SearchSection> sections = new ArrayList<>();
        for (SearchResultType type : SearchResultType.values()) {
            Optional<Page<SearchResult>> found = searchOne(type, context, firstPage);
            if (found.isPresent() && found.get().getTotalElements() > 0 && found.get().hasContent()) {
                Page<SearchResult> page = found.get();
                sections.add(new SearchSection(type, type.label(), page.getTotalElements(), page.getContent()));
            }
        }
        return new GlobalSearchResponse(context.text().text(), sections);
    }

    /**
     * One page of one kind, under exactly the rules of the preview. A kind the actor may not search is an empty page,
     * never a 403 or 404, so the answer reveals nothing about records the actor cannot see.
     */
    public SearchResultPage results(SearchActor actor, String query, SearchResultType type, int page, int size) {
        SearchText text = SearchText.of(query);
        if (type == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, TYPE_REQUIRED);
        }
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The page must be 0 or more.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        PageRequest request = PageRequest.of(page, size);
        Page<SearchResult> found = searchOne(type, new SearchContext(actor, text), request)
                .orElseGet(() -> Page.empty(request));
        return new SearchResultPage(text.text(), type, type.label(), found.getContent(), request.getPageNumber(),
                request.getPageSize(), found.getTotalElements(), found.getTotalPages());
    }

    private Optional<Page<SearchResult>> searchOne(SearchResultType type, SearchContext context, PageRequest page) {
        SearchProvider provider = providers.get(type);
        return provider == null ? Optional.empty() : provider.search(type, context, page);
    }
}
