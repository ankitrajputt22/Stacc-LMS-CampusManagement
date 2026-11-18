import { SEARCH_RESULT_TYPES, type SearchResultType } from '../types/search'

export const SEARCH_MIN = 2
export const SEARCH_MAX = 100

export function normalizeQuery(raw: string | null): string {
  return (raw ?? '').trim().replace(/\s+/g, ' ')
}

export function isSearchable(query: string): boolean {
  return query.replace(/ /g, '').length >= SEARCH_MIN && query.length <= SEARCH_MAX
}

export function parseType(raw: string | null): SearchResultType | null {
  return SEARCH_RESULT_TYPES.find((type) => type === raw) ?? null
}

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
