import { type QueryKey, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { getHttpStatus } from './apiError'
import type { ApiRequestState } from './useApiRequest'

/**
 * Loads one backend answer through the shared query cache and says what the page should show, in the same states as
 * useApiRequest, so a page can switch without changing how it renders. Pages that show the same answer share one
 * request and one cached copy.
 *
 * retry() starts again from scratch and shows loading meanwhile; refresh() asks again quietly and keeps what is on
 * screen until the new answer arrives. A key of null means there is nothing valid to ask for: nothing is sent and the
 * state is notFound. A 401 stays loading: the shared client has already ended the session and sign-in is on its way.
 * New code uses this instead of useApiRequest; existing pages move over when they are redesigned.
 */
export function useApiQuery<T>(
  queryKey: QueryKey | null,
  request: (signal: AbortSignal) => Promise<T>,
): ApiRequestState<T> & { retry: () => void; refresh: () => void } {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: queryKey ?? ['nothing-to-ask'],
    queryFn: ({ signal }) => request(signal),
    enabled: queryKey !== null,
  })

  const retry = useCallback(() => {
    if (queryKey !== null) {
      void queryClient.resetQueries({ queryKey, exact: true })
    }
  }, [queryClient, queryKey])
  const { refetch } = query
  const refresh = useCallback(() => {
    void refetch()
  }, [refetch])

  if (queryKey === null) {
    return { status: 'notFound', retry, refresh }
  }
  if (query.isSuccess) {
    return { status: 'loaded', data: query.data, retry, refresh }
  }
  if (query.isError) {
    const status = getHttpStatus(query.error)
    if (status === 401) {
      return { status: 'loading', retry, refresh }
    }
    return { status: status === 403 ? 'denied' : status === 404 ? 'notFound' : 'failed', retry, refresh }
  }
  return { status: 'loading', retry, refresh }
}
