import { SEARCH_RESULT_TYPES, type SearchResultType } from '../types/search'

// The same rules as the backend's SearchText, so the page never sends a query the backend would refuse.

export const SEARCH_MIN = 2
export const SEARCH_MAX = 100

/** Trimmed, with repeated spaces collapsed. */
export function normalizeQuery(raw: string | null): string {
  return (raw ?? '').trim().replace(/\s+/g, ' ')
}

/** Whether a normalized query has at least 2 characters that are not spaces. */
export function isSearchable(query: string): boolean {
  return query.replace(/ /g, '').length >= SEARCH_MIN && query.length <= SEARCH_MAX
}

export function parseType(raw: string | null): SearchResultType | null {
  return SEARCH_RESULT_TYPES.find((type) => type === raw) ?? null
}

/** The search page's address for a query, and optionally one kind of result and its page. */
export function searchPath(query: string, type?: SearchResultType, page?: number): string {
  const params = new URLSearchParams({ q: query })
  if (type) {
    params.set('type', type)
    if (page) {
      params.set('page', String(page))
    }
  }
  return `/search?${params.toString()}`
}
