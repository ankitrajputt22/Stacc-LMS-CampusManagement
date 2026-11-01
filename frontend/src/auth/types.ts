import type { AccountRole } from './roles'

/**
 * Who is signed in, as the backend says on sign-in and on GET /api/v1/auth/session. Mirrors SessionResponse in
 * com.stacc.backend.auth.api. The token itself never reaches the page: it lives in an HttpOnly cookie.
 */
export type SessionResponse = {
  accountId: number
  loginId: string
  roles: string[]
  /** When the sign-in ends at the latest (ISO 8601). It can end earlier, after the idle timeout or a sign-out. */
  expiresAt: string
  /** True when the college requires a new password. Until it is changed, only changing it and signing out work. */
  passwordChangeRequired: boolean
}

/** Who is signed in. It is shown in the interface and grants nothing by itself. */
export type AuthAccount = {
  accountId: number
  loginId: string
  /** The roles this app knows, in a fixed order. No role is the main one. */
  roles: AccountRole[]
}

/** The signed-in session as the page keeps it in memory. Nothing of it is ever written to browser storage. */
export type AuthSession = {
  accountId: number
  loginId: string
  /** The role names exactly as the backend sent them. */
  roles: string[]
  /** When the session ends at the latest, in milliseconds since 1970. */
  expiresAt: number
  /** The college requires a new password before anything else. */
  passwordChangeRequired: boolean
}
