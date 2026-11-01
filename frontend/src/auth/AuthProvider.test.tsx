import { QueryClientProvider } from '@tanstack/react-query'
import { act, render, waitFor } from '@testing-library/react'
import { use } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/authApi'
import { testQueryClient } from '../test/queryTestUtils'
import { AuthContext, type AuthContextValue } from './AuthContext'
import { AuthProvider } from './AuthProvider'

vi.mock('../api/authApi', () => ({
  getCurrentSession: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  logoutEverywhere: vi.fn(),
}))

const inAnHour = () => new Date(Date.now() + 3_600_000).toISOString()

describe('AuthProvider', () => {
  let auth: AuthContextValue | null = null

  function Capture() {
    auth = use(AuthContext)
    return null
  }

  function renderProvider() {
    const client = testQueryClient()
    client.setQueryData(['lms', 'my-courses'], ['Data Structures'])
    render(
      <QueryClientProvider client={client}>
        <AuthProvider>
          <Capture />
        </AuthProvider>
      </QueryClientProvider>,
    )
    return client
  }

  beforeEach(() => {
    auth = null
    vi.mocked(authApi.getCurrentSession).mockResolvedValue({
      accountId: 1, loginId: 'student1', roles: ['STUDENT'], expiresAt: inAnHour(),
    })
    vi.mocked(authApi.login).mockResolvedValue({
      accountId: 2, loginId: 'student2', roles: ['STUDENT'], expiresAt: inAnHour(),
    })
    vi.mocked(authApi.logout).mockResolvedValue()
    vi.mocked(authApi.logoutEverywhere).mockResolvedValue()
    window.localStorage.clear()
    window.sessionStorage.clear()
  })

  it('asks the backend who is signed in, and shows nothing protected until it answers', async () => {
    renderProvider()

    expect(auth?.isChecking).toBe(true)
    expect(auth?.isAuthenticated).toBe(false)
    await waitFor(() => expect(auth?.isAuthenticated).toBe(true))
    expect(auth?.isChecking).toBe(false)
    expect(auth?.account).toEqual({ accountId: 1, loginId: 'student1', roles: ['STUDENT'] })
  })

  it('treats no session as signed out', async () => {
    vi.mocked(authApi.getCurrentSession).mockRejectedValue(new Error('401'))
    renderProvider()

    await waitFor(() => expect(auth?.isChecking).toBe(false))
    expect(auth?.isAuthenticated).toBe(false)
  })

  it('keeps nothing about the session in browser storage', async () => {
    renderProvider()
    await waitFor(() => expect(auth?.isAuthenticated).toBe(true))
    await act(async () => auth?.login('student2', 'a-password'))

    expect(window.sessionStorage.length).toBe(0)
    expect(window.localStorage.length).toBe(0)
  })

  it('signs out on the server, then forgets the session and every cached answer', async () => {
    const client = renderProvider()
    await waitFor(() => expect(auth?.isAuthenticated).toBe(true))

    await act(async () => auth?.logout())

    expect(authApi.logout).toHaveBeenCalledTimes(1)
    expect(auth?.isAuthenticated).toBe(false)
    expect(auth?.signedOutByUser).toBe(true)
    expect(client.getQueryData(['lms', 'my-courses'])).toBeUndefined()
  })

  it('forgets the session even when the sign-out request fails', async () => {
    vi.mocked(authApi.logout).mockRejectedValue(new Error('offline'))
    renderProvider()
    await waitFor(() => expect(auth?.isAuthenticated).toBe(true))

    await act(async () => auth?.logout())

    expect(auth?.isAuthenticated).toBe(false)
  })

  it('forgets every cached answer when someone else signs in', async () => {
    const client = renderProvider()
    await waitFor(() => expect(auth?.isAuthenticated).toBe(true))

    await act(async () => auth?.login('student2', 'a-password'))

    expect(auth?.account?.loginId).toBe('student2')
    expect(client.getQueryData(['lms', 'my-courses'])).toBeUndefined()
  })
})
