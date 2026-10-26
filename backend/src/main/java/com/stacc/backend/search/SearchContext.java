package com.stacc.backend.search;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One search request: who is searching, the text, and what the providers looked up for this account already, such as
 * the student's accessible courses, so a preview asks for them once rather than once per section. It lives for one
 * request and is never stored.
 */
public final class SearchContext {

    private final SearchActor actor;
    private final SearchText text;
    private final Map<String, Object> memo = new HashMap<>();

    public SearchContext(SearchActor actor, SearchText text) {
        this.actor = actor;
        this.text = text;
    }

    public SearchActor actor() {
        return actor;
    }

    public SearchText text() {
        return text;
    }

    /** The value under this key, looked up once per request. */
    @SuppressWarnings("unchecked")
    public <T> T remember(String key, Supplier<T> lookup) {
        if (!memo.containsKey(key)) {
            memo.put(key, lookup.get());
        }
        return (T) memo.get(key);
    }
}
