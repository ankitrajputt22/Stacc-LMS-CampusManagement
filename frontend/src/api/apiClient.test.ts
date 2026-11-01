import { AxiosError, AxiosHeaders, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, loadSession, saveSession } from '../auth/authStorage'
import type { AuthSession } from '../auth/types'
import { apiBaseUrl, apiClient, onUnauthorized } from './apiClient'

const session: AuthSession = {
  accountId: 1,
  loginId: 'student1',
  roles: ['STUDENT'],
  accessToken: 'current-token',
  expiresAt: 2_000_000_000_000,
}

const originalAdapter = apiClient.defaults.adapter

/** Answers every request with this status instead of calling a server, after an optional step in between. */
function answerWith(status: number, meanwhile?: () => void): { sent: InternalAxiosRequestConfig[] } {
  const sent: InternalAxiosRequestConfig[] = []
  const adapter: AxiosAdapter = async (config) => {
    sent.push(config)
    meanwhile?.()
    const response = { status, statusText: '', headers: new AxiosHeaders(), config, data: {} }
    if (status < 400) {
      return response
    }
    throw new AxiosError('Request failed', AxiosError.ERR_BAD_REQUEST, config, null, response)
  }
  apiClient.defaults.adapter = adapter
  return { sent }
}

describe('apiClient', () => {
  beforeEach(() => {
    clearSession()
  })

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
  })

  it('sends every request to the versioned API', async () => {
    const { sent } = answerWith(200)

    await apiClient.get('/lms/my-courses')

    expect(apiBaseUrl).toBe('http://localhost:8080/api/v1')
    expect(apiClient.getUri(sent[0])).toBe('http://localhost:8080/api/v1/lms/my-courses')
  })

  it('sends the access token only while signed in', async () => {
    const { sent } = answerWith(200)

    await apiClient.get('/lms/my-courses')
    saveSession(session)
    await apiClient.get('/lms/my-courses')

    expect(sent[0].headers.get('Authorization')).toBeUndefined()
    expect(sent[1].headers.get('Authorization')).toBe('Bearer current-token')
    expect(sent[1].headers.get('Accept')).toBe('application/json')
  })

  it('ends the session when the backend refuses the current token', async () => {
    saveSession(session)
    const signedOut = vi.fn()
    const stop = onUnauthorized(signedOut)
    answerWith(401)

    await expect(apiClient.get('/dashboard/student')).rejects.toBeInstanceOf(AxiosError)

    expect(loadSession()).toBeNull()
    expect(signedOut).toHaveBeenCalledTimes(1)
    stop()
  })

  it('treats a failed login as a form error, not a sign-out', async () => {
    const signedOut = vi.fn()
    const stop = onUnauthorized(signedOut)
    answerWith(401)

    await expect(apiClient.post('/auth/login', {})).rejects.toBeInstanceOf(AxiosError)

    expect(signedOut).not.toHaveBeenCalled()
    stop()
  })

  it('never signs anyone out on 403, which only means "not allowed"', async () => {
    saveSession(session)
    const signedOut = vi.fn()
    const stop = onUnauthorized(signedOut)
    answerWith(403)

    await expect(apiClient.get('/reports/admin/overview')).rejects.toBeInstanceOf(AxiosError)

    expect(loadSession()).toEqual(session)
    expect(signedOut).not.toHaveBeenCalled()
    stop()
  })

  it('keeps a newer session when a request sent with an older token is refused', async () => {
    saveSession({ ...session, accessToken: 'old-token' })
    const signedOut = vi.fn()
    const stop = onUnauthorized(signedOut)
    // The user signs in again while the old request is still on its way.
    answerWith(401, () => saveSession(session))

    await expect(apiClient.get('/inbox')).rejects.toBeInstanceOf(AxiosError)

    expect(loadSession()).toEqual(session)
    expect(signedOut).not.toHaveBeenCalled()
    stop()
  })
})
