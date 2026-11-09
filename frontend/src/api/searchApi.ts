import type { GlobalSearchResponse, SearchResultPage, SearchResultType } from '../types/search'
import { apiClient } from './apiClient'

// Search runs only when the user submits: never per keystroke. The account, its roles, and its permissions come from
// the access token that only the client sends; no request names an account.

export async function searchStacc(query: string, signal?: AbortSignal) {
  return (await apiClient.get<GlobalSearchResponse>('/search', { params: { q: query }, signal })).data
}

export async function searchStaccByType(query: string, type: SearchResultType, page: number, size: number, signal?: AbortSignal) {
  return (await apiClient.get<SearchResultPage>('/search/results', { params: { q: query, type, page, size }, signal })).data
}
