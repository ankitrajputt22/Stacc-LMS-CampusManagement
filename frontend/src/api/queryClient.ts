import { QueryClient } from '@tanstack/react-query'
import { getHttpStatus } from './apiError'

const FINAL_STATUSES = new Set([401, 403, 404])

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        gcTime: 5 * 60_000,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        retry: (failureCount, error) => !FINAL_STATUSES.has(getHttpStatus(error) ?? 0) && failureCount < 1,
      },
      mutations: {
        retry: false,
      },
    },
  })
}
