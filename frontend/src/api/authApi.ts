import type { SessionResponse } from '../auth/types'
import { apiClient } from './apiClient'

export async function login(loginId: string, password: string): Promise<SessionResponse> {
  const response = await apiClient.post<SessionResponse>('/auth/login', { loginId, password })
  return response.data
}

export async function getCurrentSession(signal?: AbortSignal): Promise<SessionResponse> {
  const response = await apiClient.get<SessionResponse>('/auth/session', { signal })
  return response.data
}

export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout')
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await apiClient.post('/me/password', { currentPassword, newPassword })
}

export async function logoutEverywhere(): Promise<void> {
  await apiClient.post('/auth/logout-everywhere')
}
