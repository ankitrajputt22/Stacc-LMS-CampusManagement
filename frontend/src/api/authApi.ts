import type { SessionResponse } from '../auth/types'
import { apiClient } from './apiClient'

/**
 * Signs in with college credentials. The values are sent exactly as typed: the backend decides how a login ID is
 * compared, and a password is never trimmed or changed. The backend sets the HttpOnly session cookie; the answer only
 * says who signed in. POST /api/v1/auth/login.
 */
export async function login(loginId: string, password: string): Promise<SessionResponse> {
  const response = await apiClient.post<SessionResponse>('/auth/login', { loginId, password })
  return response.data
}

/** Who is signed in according to the session cookie, or a 401 when nobody is. GET /api/v1/auth/session. */
export async function getCurrentSession(signal?: AbortSignal): Promise<SessionResponse> {
  const response = await apiClient.get<SessionResponse>('/auth/session', { signal })
  return response.data
}

/** Ends this browser's session on the server and clears its cookie. POST /api/v1/auth/logout. */
export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout')
}

/**
 * Changes the signed-in person's password. Both values are sent exactly as typed. Every other device is signed out;
 * this one stays signed in. POST /api/v1/me/password.
 */
export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await apiClient.post('/me/password', { currentPassword, newPassword })
}

/** Ends every session of this account, on every device. POST /api/v1/auth/logout-everywhere. */
export async function logoutEverywhere(): Promise<void> {
  await apiClient.post('/auth/logout-everywhere')
}
