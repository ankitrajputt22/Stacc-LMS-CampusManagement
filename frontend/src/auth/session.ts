import type { AuthSession, SessionResponse } from './types'

/** Turns the backend's answer into the session the page keeps in memory. */
export function sessionFrom(response: SessionResponse): AuthSession {
  const expiresAt = Date.parse(response.expiresAt)
  if (!response.loginId || !Number.isFinite(expiresAt)) {
    throw new Error('The sign-in answer did not describe a usable session.')
  }

  return {
    accountId: response.accountId,
    loginId: response.loginId,
    roles: response.roles,
    expiresAt,
    passwordChangeRequired: response.passwordChangeRequired === true,
  }
}

/** The backend has the final word on a session. This only spares it requests that cannot succeed. */
export function isSessionExpired(session: AuthSession, now: number = Date.now()): boolean {
  return session.expiresAt <= now
}
