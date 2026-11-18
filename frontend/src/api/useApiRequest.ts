import { useCallback, useEffect, useState } from 'react'
import { getHttpStatus } from './apiError'

export type ApiRequestState<T> =
  | { status: 'loading' }
  | { status: 'loaded'; data: T }
  | { status: 'notFound' }
  | { status: 'denied' }
  | { status: 'failed' }

type Request<T> = (signal: AbortSignal) => Promise<T>

type Answer<T> = {
  request: Request<T>
  attempt: number
  state: Exclude<ApiRequestState<T>, { status: 'loading' }>
}

export function useApiRequest<T>(
  request: Request<T> | null,
): ApiRequestState<T> & { retry: () => void; refresh: () => void } {
  const [attempt, setAttempt] = useState(0)
  const [refreshes, setRefreshes] = useState(0)
  const [answer, setAnswer] = useState<Answer<T> | null>(null)

  useEffect(() => {
    if (request === null) {
      return
    }

    const controller = new AbortController()

    request(controller.signal).then(
      (data) => setAnswer({ request, attempt, state: { status: 'loaded', data } }),
      (error: unknown) => {
        if (controller.signal.aborted) {
          return
        }

        const status = getHttpStatus(error)
        if (status === 401) {
          return
        }

        setAnswer({
          request,
          attempt,
          state: {
            status: status === 403 ? 'denied' : status === 404 ? 'notFound' : 'failed',
          },
        })
      },
    )

    return () => controller.abort()
  }, [request, attempt, refreshes])

  const retry = useCallback(() => setAttempt((previous) => previous + 1), [])
  const refresh = useCallback(() => setRefreshes((previous) => previous + 1), [])

  if (request === null) {
    return { status: 'notFound', retry, refresh }
  }

  const isCurrent =
    answer !== null && answer.request === request && answer.attempt === attempt

  return { ...(isCurrent ? answer.state : { status: 'loading' }), retry, refresh }
}
