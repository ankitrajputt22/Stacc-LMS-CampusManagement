import { createContext, useContext } from 'react'
import type { AuthAccount } from './types'

export type AuthContextValue = {
  /** True until the backend has said who is signed in, once per page load. */
  isChecking: boolean
  isAuthenticated: boolean
  /**
   * The signed-in account, or null. Use it for display only: the backend decides what is allowed.
   * Its roles are the known ones in a fixed order, and an account may hold several.
   */
  account: AuthAccount | null
  /** True after the user chose "Sign out", until someone signs in again. */
  signedOutByUser: boolean
  /** Signs in with college credentials. It rejects with the request error when the login fails. */
  login: (loginId: string, password: string) => Promise<void>
  /** Ends this browser's session on the server, then forgets it here, whatever the answer. */
  logout: () => Promise<void>
  /** Ends every session of this account, on every device. */
  logoutEverywhere: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)

  if (value === null) {
    throw new Error('useAuth must be used inside AuthProvider.')
  }

  return value
}
