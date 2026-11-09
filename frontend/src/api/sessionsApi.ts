import type { MySession } from '../types/sessions'
import { apiClient } from './apiClient'

// The account is the one in the session cookie; nothing here names an account.

export async function getMySessions(signal?: AbortSignal): Promise<MySession[]> {
  return (await apiClient.get<MySession[]>('/me/sessions', { signal })).data
}

/** Signs out one other device at once. The current session is ended with Sign out instead. */
export async function endMySession(sessionId: string): Promise<void> {
  await apiClient.post(`/me/sessions/${encodeURIComponent(sessionId)}/end`)
}
