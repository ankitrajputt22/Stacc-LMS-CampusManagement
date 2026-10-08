import type { AuthSession, LoginResponse } from './types'

/**
 * Turns a successful login into the session kept for this tab. The backend says how long
 * the token lasts, so the end time is worked out from that. The token itself is not read.
 */
export function sessionFromLogin(
  response: LoginResponse,
  now: number = Date.now(),
): AuthSession {
  if (!response.accessToken || !(response.expiresIn > 0)) {
    throw new Error('The login response did not contain a usable access token.')
  }

  return {
    accountId: response.accountId,
    loginId: response.loginId,
    roles: response.roles,
    accessToken: response.accessToken,
    expiresAt: now + response.expiresIn * 1000,
  }
}

/** The backend has the final word on a token. This only spares it requests that cannot succeed. */
export function isSessionExpired(
  session: AuthSession,
  now: number = Date.now(),
): boolean {
  return session.expiresAt <= now
}
