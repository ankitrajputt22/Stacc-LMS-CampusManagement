import type { MySession } from '../types/sessions'
import { apiClient } from './apiClient'

export async function getMySessions(signal?: AbortSignal): Promise<MySession[]> {
  return (await apiClient.get<MySession[]>('/me/sessions', { signal })).data
}

export async function endMySession(sessionId: string): Promise<void> {
  await apiClient.post(`/me/sessions/${encodeURIComponent(sessionId)}/end`)
}
