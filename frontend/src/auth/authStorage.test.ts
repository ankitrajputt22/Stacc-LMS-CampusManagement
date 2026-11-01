import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthSession } from './types'

const STORAGE_KEY = 'stacc.auth.session'

const session: AuthSession = {
  accountId: 3,
  loginId: 'faculty1',
  roles: ['FACULTY'],
  accessToken: 'token-value',
  expiresAt: 2_000_000_000_000,
}

// The module remembers what it read, so each test loads a fresh copy, as a page load would.
async function freshStorage() {
  vi.resetModules()
  return import('./authStorage')
}

describe('authStorage', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
  })

  it('keeps the session for this tab only, never in localStorage', async () => {
    const storage = await freshStorage()
    storage.saveSession(session)

    expect(JSON.parse(window.sessionStorage.getItem(STORAGE_KEY) ?? 'null')).toEqual(session)
    expect(window.localStorage.length).toBe(0)
    expect((await freshStorage()).loadSession()).toEqual(session)
  })

  it('forgets the session on sign-out', async () => {
    const storage = await freshStorage()
    storage.saveSession(session)
    storage.clearSession()

    expect(storage.loadSession()).toBeNull()
    expect(window.sessionStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('throws away damaged or incomplete saved data', async () => {
    window.sessionStorage.setItem(STORAGE_KEY, '{not json')
    expect((await freshStorage()).loadSession()).toBeNull()
    expect(window.sessionStorage.getItem(STORAGE_KEY)).toBeNull()

    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ ...session, accessToken: '' }))
    expect((await freshStorage()).loadSession()).toBeNull()

    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ ...session, roles: ['STUDENT', 5] }))
    expect((await freshStorage()).loadSession()).toBeNull()
  })
})
