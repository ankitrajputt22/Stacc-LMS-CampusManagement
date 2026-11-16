import type { Idea, IdeaStatus } from '../types/innovation'
import { apiClient } from './apiClient'

// The submitter and reviewer are always the signed-in account; the backend checks every idea.

export const getMyIdeas = async (signal?: AbortSignal) => (await apiClient.get<Idea[]>('/innovation/ideas/me', { signal })).data
export const submitIdea = async (input: { title: string; problem: string; solution: string }) =>
  (await apiClient.post<Idea>('/innovation/ideas', input)).data
export const withdrawIdea = async (ideaId: number) => (await apiClient.post<Idea>(`/innovation/ideas/${ideaId}/withdraw`)).data
export const getIdeasToReview = async (signal?: AbortSignal) => (await apiClient.get<Idea[]>('/innovation/reviews', { signal })).data
export async function reviewIdea(ideaId: number, score: number, comment: string) {
  await apiClient.post(`/innovation/ideas/${ideaId}/review`, { score, comment })
}
export const getAllIdeas = async (signal?: AbortSignal) => (await apiClient.get<Idea[]>('/admin/innovation/ideas', { signal })).data
export async function decideIdea(ideaId: number, status: IdeaStatus, note: string | null) {
  await apiClient.post(`/admin/innovation/ideas/${ideaId}/decision`, { status, note })
}
