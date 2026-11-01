import { QueryClientProvider } from '@tanstack/react-query'
import { act, render } from '@testing-library/react'
import { use } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { testQueryClient } from '../test/queryTestUtils'
import { AuthContext, type AuthContextValue } from './AuthContext'
import { saveSession } from './authStorage'
import { AuthProvider } from './AuthProvider'

vi.mock('../api/authApi', () => ({
  login: vi.fn(async (loginId: string) => ({
    accountId: 2, loginId, roles: ['STUDENT'], accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 900,
  })),
}))

describe('AuthProvider and the query cache', () => {
  let auth: AuthContextValue | null = null

  function Capture() {
    auth = use(AuthContext)
    return null
  }

  beforeEach(() => {
    saveSession({ accountId: 1, loginId: 'student1', roles: ['STUDENT'], accessToken: 'token', expiresAt: Date.now() + 600_000 })
  })

  function renderWithCache() {
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

  it('forgets every cached answer when the user signs out', () => {
    const client = renderWithCache()

    act(() => auth?.logout())

    expect(client.getQueryData(['lms', 'my-courses'])).toBeUndefined()
  })

  it('forgets every cached answer when someone else signs in', async () => {
    const client = renderWithCache()

    await act(async () => auth?.login('student2', 'a-password'))

    expect(client.getQueryData(['lms', 'my-courses'])).toBeUndefined()
  })
})
