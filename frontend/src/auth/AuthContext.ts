import { createContext, useContext } from 'react'
import type { AuthAccount } from './types'

export type AuthContextValue = {
  isAuthenticated: boolean
  /** The signed-in account, or null. Use it for display only: the backend decides what is allowed. */
  account: AuthAccount | null
  /** True after the user chose "Sign out", until someone signs in again. */
  signedOutByUser: boolean
  /** Signs in with college credentials. It rejects with the request error when the login fails. */
  login: (loginId: string, password: string) => Promise<void>
  /** Forgets the session in this tab. The backend has no sign-out, so the token is simply no longer used. */
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)

  if (value === null) {
    throw new Error('useAuth must be used inside AuthProvider.')
  }

  return value
}
