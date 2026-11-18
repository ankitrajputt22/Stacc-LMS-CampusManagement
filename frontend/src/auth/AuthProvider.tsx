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

type SessionState = { status: 'checking' } | { status: 'known'; session: AuthSession | null }

export function AuthProvider({ children }: AuthProviderProps) {
  const [state, setState] = useState<SessionState>({ status: 'checking' })
  const [signedOutByUser, setSignedOutByUser] = useState(false)
  const queryClient = useQueryClient()

  const endSession = useCallback(() => {
    queryClient.clear()
    setState({ status: 'known', session: null })
  }, [queryClient])

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

  const logout = useCallback(async () => {
    setSignedOutByUser(true)
    try {
      await requestLogout()
    } catch {
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

  useEffect(() => onUnauthorized(endSession), [endSession])

  const session = state.status === 'known' ? state.session : null

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
