package com.stacc.backend.search;

import java.util.Set;

/** Actors and contexts for the search tests. */
final class SearchTestSupport {

    private SearchTestSupport() {
    }

    static SearchActor actor(long accountId, Set<String> roles, String... permissions) {
        return new SearchActor(accountId, roles, Set.of(permissions));
    }

    static SearchContext context(SearchActor actor, String query) {
        return new SearchContext(actor, SearchText.of(query));
    }
}
