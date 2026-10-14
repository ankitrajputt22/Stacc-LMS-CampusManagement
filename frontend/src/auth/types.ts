import type { AccountRole } from './roles'

/** The backend's answer to a successful login. Mirrors LoginResponse in com.stacc.backend.auth.api. */
export type LoginResponse = {
  accountId: number
  loginId: string
  roles: string[]
  accessToken: string
  tokenType: string
  /** How long the access token stays valid, in seconds. */
  expiresIn: number
}

/** Who is signed in. It is shown in the interface and grants nothing by itself. */
export type AuthAccount = {
  accountId: number
  loginId: string
  /** The roles this app knows, in a fixed order. No role is the main one. */
  roles: AccountRole[]
}

/** Everything kept for a signed-in browser tab. It never holds a password. */
export type AuthSession = {
  accountId: number
  loginId: string
  /** The role names exactly as the backend sent them. */
  roles: string[]
  accessToken: string
  /** When the access token stops being valid, in milliseconds since 1970. */
  expiresAt: number
}
