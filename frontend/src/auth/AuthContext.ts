import { createContext, useContext } from 'react'
import type { AuthAccount } from './types'

export type AuthContextValue = {
  isChecking: boolean
  isAuthenticated: boolean
  account: AuthAccount | null
  passwordChangeRequired: boolean
  passwordChanged: () => void
  signedOutByUser: boolean
  login: (loginId: string, password: string) => Promise<void>
  logout: () => Promise<void>
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
