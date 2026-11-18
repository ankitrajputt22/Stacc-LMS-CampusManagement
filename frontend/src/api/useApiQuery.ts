import { type QueryKey, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { getHttpStatus } from './apiError'
import type { ApiRequestState } from './useApiRequest'

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
