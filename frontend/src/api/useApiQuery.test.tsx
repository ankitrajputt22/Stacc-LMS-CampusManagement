import { act, renderHook, waitFor } from '@testing-library/react'
import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { createQueryClient } from './queryClient'
import { testQueryClient, withQueryClient } from '../test/queryTestUtils'
import { useApiQuery } from './useApiQuery'

function httpError(status: number): AxiosError {
  const config = { headers: new AxiosHeaders() }
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, null, {
    status, statusText: '', headers: {}, config, data: {},
  })
}

describe('useApiQuery', () => {
  it('shows loading, then the answer', async () => {
    const client = testQueryClient()
    const { result } = renderHook(() => useApiQuery(['example'], async () => ['CS301']), { wrapper: withQueryClient(client) })

    expect(result.current.status).toBe('loading')
    await waitFor(() => expect(result.current.status).toBe('loaded'))
    expect(result.current).toMatchObject({ status: 'loaded', data: ['CS301'] })
  })

  it('turns 403 into denied and 404 into notFound, and keeps loading on 401 while sign-in takes over', async () => {
    const client = testQueryClient()
    const wrapper = withQueryClient(client)
    const denied = renderHook(() => useApiQuery(['a'], () => Promise.reject(httpError(403))), { wrapper })
    const missing = renderHook(() => useApiQuery(['b'], () => Promise.reject(httpError(404))), { wrapper })
    const failed = renderHook(() => useApiQuery(['c'], () => Promise.reject(httpError(500))), { wrapper })
    const unauthorized = renderHook(() => useApiQuery(['d'], () => Promise.reject(httpError(401))), { wrapper })

    await waitFor(() => expect(denied.result.current.status).toBe('denied'))
    await waitFor(() => expect(missing.result.current.status).toBe('notFound'))
    await waitFor(() => expect(failed.result.current.status).toBe('failed'))
    await waitFor(() => expect(client.getQueryState(['d'])?.status).toBe('error'))
    expect(unauthorized.result.current.status).toBe('loading')
  })

  it('sends nothing when there is nothing valid to ask for', () => {
    const request = vi.fn(async () => 'never')
    const { result } = renderHook(() => useApiQuery(null, request), { wrapper: withQueryClient(testQueryClient()) })

    expect(result.current.status).toBe('notFound')
    expect(request).not.toHaveBeenCalled()
  })

  it('lets pages showing the same answer share one request', async () => {
    const client = testQueryClient()
    const request = vi.fn(async () => 'shared')
    const wrapper = withQueryClient(client)
    const first = renderHook(() => useApiQuery(['shared'], request), { wrapper })
    const second = renderHook(() => useApiQuery(['shared'], request), { wrapper })

    await waitFor(() => expect(first.result.current.status).toBe('loaded'))
    await waitFor(() => expect(second.result.current.status).toBe('loaded'))
    expect(request).toHaveBeenCalledTimes(1)
  })

  it('retry() starts again from scratch, showing loading until the new answer', async () => {
    const client = testQueryClient()
    let attempt = 0
    let answer: (value: string) => void = () => {}
    const { result } = renderHook(
      () => useApiQuery(['flaky'], async () => {
        attempt += 1
        if (attempt === 1) {
          throw httpError(500)
        }
        return new Promise<string>((resolve) => {
          answer = resolve
        })
      }),
      { wrapper: withQueryClient(client) },
    )
    await waitFor(() => expect(result.current.status).toBe('failed'))

    act(() => result.current.retry())

    await waitFor(() => expect(result.current.status).toBe('loading'))
    act(() => answer('second time lucky'))
    await waitFor(() => expect(result.current).toMatchObject({ status: 'loaded', data: 'second time lucky' }))
  })

  it('does not try again by itself after 401, 403, or 404', () => {
    const retry = createQueryClient().getDefaultOptions().queries?.retry
    expect(typeof retry).toBe('function')
    const decide = retry as (failureCount: number, error: unknown) => boolean
    expect(decide(0, httpError(401))).toBe(false)
    expect(decide(0, httpError(403))).toBe(false)
    expect(decide(0, httpError(404))).toBe(false)
    expect(decide(0, httpError(503))).toBe(true)
    expect(decide(1, httpError(503))).toBe(false)
  })
})
