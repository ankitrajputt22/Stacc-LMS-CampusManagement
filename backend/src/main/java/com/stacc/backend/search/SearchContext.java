package com.stacc.backend.search;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

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

    @SuppressWarnings("unchecked")
    public <T> T remember(String key, Supplier<T> lookup) {
        if (!memo.containsKey(key)) {
            memo.put(key, lookup.get());
        }
        return (T) memo.get(key);
    }
}
