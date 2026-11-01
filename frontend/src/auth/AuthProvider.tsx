import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { onUnauthorized } from '../api/apiClient'
import {
  getCurrentSession,
  login as requestLogin,
  logout as requestLogout,
  logoutEverywhere as requestLogoutEverywhere,
} from '../api/authApi'
import { AuthContext, type AuthContextValue } from './AuthContext'
import { toAccountRoles } from './roles'
import { isSessionExpired, sessionFrom } from './session'
import type { AuthSession } from './types'

type AuthProviderProps = {
  children: ReactNode
}

// While the backend has not yet said who is signed in, the session is unknown: nothing protected is shown, and nobody
// is sent to the sign-in page either.
type SessionState = { status: 'checking' } | { status: 'known'; session: AuthSession | null }

/**
 * Holds who is signed in for the whole app. The backend is the only record: the session lives in an HttpOnly cookie
 * the page cannot read, so on every page load the provider asks GET /api/v1/auth/session once. Nothing about the session
 * is written to browser storage. It is a convenience for the interface only: every request is still checked by the
 * backend, whatever this state says.
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const [state, setState] = useState<SessionState>({ status: 'checking' })
  // A session can end because the user chose to sign out or because it ran out. Only the first is remembered: the
  // sign-in page treats the two differently.
  const [signedOutByUser, setSignedOutByUser] = useState(false)
  // Cached backend answers belong to the session that asked for them: they go when it ends or another one starts.
  const queryClient = useQueryClient()

  const endSession = useCallback(() => {
    queryClient.clear()
    setState({ status: 'known', session: null })
  }, [queryClient])

  // Ask the backend once who is signed in. No answer, or 401, means nobody.
  useEffect(() => {
    const controller = new AbortController()
    getCurrentSession(controller.signal).then(
      (response) => setState({ status: 'known', session: sessionFrom(response) }),
      () => {
        if (!controller.signal.aborted) {
          setState({ status: 'known', session: null })
        }
      },
    )
    return () => controller.abort()
  }, [])

  const login = useCallback(
    async (loginId: string, password: string) => {
      const nextSession = sessionFrom(await requestLogin(loginId, password))
      queryClient.clear()
      setSignedOutByUser(false)
      setState({ status: 'known', session: nextSession })
    },
    [queryClient],
  )

  // Signing out ends the session on the server; the page forgets it whatever the answer, so a lost connection never
  // leaves anyone looking signed in.
  const logout = useCallback(async () => {
    setSignedOutByUser(true)
    try {
      await requestLogout()
    } catch {
      // The session still ends at the idle timeout; the page forgets it now.
    } finally {
      endSession()
    }
  }, [endSession])

  const logoutEverywhere = useCallback(async () => {
    setSignedOutByUser(true)
    try {
      await requestLogoutEverywhere()
    } finally {
      endSession()
    }
  }, [endSession])

  const passwordChanged = useCallback(() => {
    setState((current) =>
      current.status === 'known' && current.session !== null
        ? { status: 'known', session: { ...current.session, passwordChangeRequired: false } }
        : current,
    )
  }, [])

  // The backend says the session has ended: signed out elsewhere, idle, or disabled.
  useEffect(() => onUnauthorized(endSession), [endSession])

  const session = state.status === 'known' ? state.session : null

  // Forget the session when it runs out, instead of waiting for a request to fail.
  useEffect(() => {
    if (session === null) {
      return
    }

    const activeSession = session

    function forgetIfExpired() {
      if (isSessionExpired(activeSession)) {
        endSession()
      }
    }

    const timer = window.setTimeout(endSession, Math.max(activeSession.expiresAt - Date.now(), 0))
    // A sleeping laptop or a background tab can delay the timer, so the time is checked again whenever the tab comes
    // back.
    document.addEventListener('visibilitychange', forgetIfExpired)
    window.addEventListener('focus', forgetIfExpired)

    return () => {
      window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', forgetIfExpired)
      window.removeEventListener('focus', forgetIfExpired)
    }
  }, [session, endSession])

  const value = useMemo<AuthContextValue>(
    () => ({
      isChecking: state.status === 'checking',
      isAuthenticated: session !== null,
      account:
        session === null
          ? null
          : {
              accountId: session.accountId,
              loginId: session.loginId,
              // The one place where the backend's role names become roles the app works with.
              roles: toAccountRoles(session.roles),
            },
      passwordChangeRequired: session?.passwordChangeRequired === true,
      passwordChanged,
      signedOutByUser,
      login,
      logout,
      logoutEverywhere,
    }),
    [state.status, session, passwordChanged, signedOutByUser, login, logout, logoutEverywhere],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
