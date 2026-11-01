import { QueryClient } from '@tanstack/react-query'
import { getHttpStatus } from './apiError'

// Answers that asking again cannot change: not signed in, not allowed, or not there.
const FINAL_STATUSES = new Set([401, 403, 404])

/**
 * The one cache of backend answers for the whole app. Answers stay fresh for half a minute, so moving between pages
 * does not ask again at once; nothing is asked again just because the tab regains focus, since Stacc does not poll.
 * A failed request is tried once more, except for answers that cannot change. The cache is emptied whenever a session
 * ends or starts (see AuthProvider), so one person's data never shows for the next in the same tab.
 */
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
