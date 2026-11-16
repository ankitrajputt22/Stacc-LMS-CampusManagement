import type { Portfolio, PortfolioItem, PortfolioItemInput, WaitingPortfolioItem } from '../types/portfolio'
import { apiClient } from './apiClient'

// The owner and the verifier are always the signed-in account; the backend checks each item.

export const getMyPortfolio = async (signal?: AbortSignal) => (await apiClient.get<Portfolio>('/portfolio/me', { signal })).data
export const addPortfolioItem = async (input: PortfolioItemInput) => (await apiClient.post<PortfolioItem>('/portfolio/items', input)).data
export const hidePortfolioItem = async (itemId: number) => (await apiClient.post<PortfolioItem>(`/portfolio/items/${itemId}/hide`)).data
export const requestPortfolioVerification = async (itemId: number, verifierLoginId: string) =>
  (await apiClient.post<PortfolioItem>(`/portfolio/items/${itemId}/verification`, { verifierLoginId })).data

export const getPortfolioVerifications = async (signal?: AbortSignal) =>
  (await apiClient.get<WaitingPortfolioItem[]>('/portfolio/verifications', { signal })).data
export async function decidePortfolioItem(itemId: number, verify: boolean, note: string | null) {
  await apiClient.post(`/portfolio/verifications/${itemId}/decision`, { verify, note })
}
