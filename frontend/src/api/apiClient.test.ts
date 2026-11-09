import { AxiosError, AxiosHeaders, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiBaseUrl, apiClient, onUnauthorized } from './apiClient'

const originalAdapter = apiClient.defaults.adapter

/** Answers every request with this status instead of calling a server. */
function answerWith(status: number): { sent: InternalAxiosRequestConfig[] } {
  const sent: InternalAxiosRequestConfig[] = []
  const adapter: AxiosAdapter = async (config) => {
    sent.push(config)
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
  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
  })

  it('sends every request to the versioned API', async () => {
    const { sent } = answerWith(200)

    await apiClient.get('/lms/my-courses')

    expect(apiBaseUrl).toBe('http://localhost:8080/api/v1')
    expect(apiClient.getUri(sent[0])).toBe('http://localhost:8080/api/v1/lms/my-courses')
  })

  it('lets the browser send the session cookie, and never sets an Authorization header itself', async () => {
    const { sent } = answerWith(200)

    await apiClient.get('/lms/my-courses')

    expect(sent[0].withCredentials).toBe(true)
    expect(sent[0].headers.get('Authorization')).toBeUndefined()
    expect(sent[0].headers.get('Accept')).toBe('application/json')
  })

  it('is set up to copy the XSRF-TOKEN cookie into the X-XSRF-TOKEN header', () => {
    // Axios copies it inside its browser adapter, which these tests replace; the end-to-end journeys exercise the copy.
    expect(apiClient.defaults.withXSRFToken).toBe(true)
    expect(apiClient.defaults.xsrfCookieName).toBe('XSRF-TOKEN')
    expect(apiClient.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN')
  })

  it('reports an ended session on a 401 from an ordinary request', async () => {
    const ended = vi.fn()
    const stop = onUnauthorized(ended)
    answerWith(401)

    await expect(apiClient.get('/dashboard/student')).rejects.toBeInstanceOf(AxiosError)

    expect(ended).toHaveBeenCalledTimes(1)
    stop()
  })

  it('treats a failed login or session check as an ordinary answer, not an ended session', async () => {
    const ended = vi.fn()
    const stop = onUnauthorized(ended)
    answerWith(401)

    await expect(apiClient.post('/auth/login', {})).rejects.toBeInstanceOf(AxiosError)
    await expect(apiClient.get('/auth/session')).rejects.toBeInstanceOf(AxiosError)

    expect(ended).not.toHaveBeenCalled()
    stop()
  })

  it('never ends the session on 403, which only means "not allowed"', async () => {
    const ended = vi.fn()
    const stop = onUnauthorized(ended)
    answerWith(403)

    await expect(apiClient.get('/reports/admin/overview')).rejects.toBeInstanceOf(AxiosError)

    expect(ended).not.toHaveBeenCalled()
    stop()
  })
})
