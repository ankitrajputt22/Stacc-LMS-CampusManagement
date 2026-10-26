/**
 * Global search: one read-only API over the data each account may already see. It owns no data and stores nothing,
 * not even the queries. Every section is answered by a provider that reuses its domain's own access rules and asks
 * the domain's repositories bounded, parameterized queries; a text match never grants access. See
 * {@code docs/search.md}.
 */
package com.stacc.backend.search;
