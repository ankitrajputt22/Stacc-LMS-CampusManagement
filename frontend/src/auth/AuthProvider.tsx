import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { onUnauthorized } from '../api/apiClient'
import { login as requestLogin } from '../api/authApi'
import { AuthContext, type AuthContextValue } from './AuthContext'
import { clearSession, loadSession, saveSession } from './authStorage'
import { isSessionExpired, sessionFromLogin } from './session'
import type { AuthSession } from './types'

// A saved session whose token has already run out is dropped before anything is shown.
function readStartingSession(): AuthSession | null {
  const saved = loadSession()

  if (saved !== null && isSessionExpired(saved)) {
    clearSession()
    return null
  }

  return saved
}

type AuthProviderProps = {
  children: ReactNode
}

/**
 * Holds who is signed in for the whole app. It is a convenience for the interface only:
 * every request is still checked by the backend, whatever this state says.
 */
export function AuthProvider({ children }: AuthProviderProps) {
  // The saved session is read during the very first render, so there is never a moment
  // in which a protected page could be shown before the answer is known.
  const [session, setSession] = useState<AuthSession | null>(readStartingSession)
  // A session can end because the user chose to sign out or because its token ran out.
  // Only the first is remembered: the sign-in page treats the two differently.
  const [signedOutByUser, setSignedOutByUser] = useState(false)

  const endSession = useCallback(() => {
    clearSession()
    setSession(null)
  }, [])

  const logout = useCallback(() => {
    setSignedOutByUser(true)
    endSession()
  }, [endSession])

  const login = useCallback(async (loginId: string, password: string) => {
    const nextSession = sessionFromLogin(await requestLogin(loginId, password))
    saveSession(nextSession)
    setSignedOutByUser(false)
    setSession(nextSession)
  }, [])

  // The backend refused the token. The saved session is already gone by the time this runs.
  useEffect(() => onUnauthorized(() => setSession(null)), [])

  // Sign out when the token runs out, instead of waiting for a request to fail.
  useEffect(() => {
    if (session === null) {
      return
    }

    const activeSession = session

    function signOutIfExpired() {
      if (isSessionExpired(activeSession)) {
        endSession()
      }
    }

    const timer = window.setTimeout(
      endSession,
      Math.max(activeSession.expiresAt - Date.now(), 0),
    )
    // A sleeping laptop or a background tab can delay the timer, so the time is checked
    // again whenever the tab comes back.
    document.addEventListener('visibilitychange', signOutIfExpired)
    window.addEventListener('focus', signOutIfExpired)

    return () => {
      window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', signOutIfExpired)
      window.removeEventListener('focus', signOutIfExpired)
    }
  }, [session, endSession])

  const value = useMemo<AuthContextValue>(
    () => ({
      isAuthenticated: session !== null,
      account:
        session === null
          ? null
          : {
              accountId: session.accountId,
              loginId: session.loginId,
              roles: session.roles,
            },
      signedOutByUser,
      login,
      logout,
    }),
    [session, signedOutByUser, login, logout],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
