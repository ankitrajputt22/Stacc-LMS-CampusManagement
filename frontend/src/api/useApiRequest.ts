import { useCallback, useEffect, useState } from 'react'
import { getHttpStatus } from './apiError'

/** What a page shows while one backend request is under way, and once it has an answer. */
export type ApiRequestState<T> =
  | { status: 'loading' }
  | { status: 'loaded'; data: T }
  /** 404: it does not exist, or it is not available to this account. */
  | { status: 'notFound' }
  /** 403: signed in, but not allowed. The user stays signed in. */
  | { status: 'denied' }
  /** No answer, a timeout, or a server error. Worth trying again. */
  | { status: 'failed' }

type Request<T> = (signal: AbortSignal) => Promise<T>

type Answer<T> = {
  request: Request<T>
  attempt: number
  state: Exclude<ApiRequestState<T>, { status: 'loading' }>
}

/**
 * Runs one backend request for a page and tracks what to show. retry() asks again.
 *
 * A request still running when the page closes, the request changes, or a retry starts is
 * cancelled, and an answer is only shown for the request and attempt it belongs to, so an old
 * answer never replaces a newer one. A 401 is left alone: the shared client has already ended
 * the session and the sign-in page is on its way.
 *
 * The request function must keep its identity between renders, for example a module function
 * or one made with useMemo, because a new function means a new request. Pass null when there is
 * nothing valid to ask for: nothing is sent, and the state is notFound.
 */
export function useApiRequest<T>(
  request: Request<T> | null,
): ApiRequestState<T> & { retry: () => void } {
  const [attempt, setAttempt] = useState(0)
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
  }, [request, attempt])

  const retry = useCallback(() => setAttempt((previous) => previous + 1), [])

  if (request === null) {
    return { status: 'notFound', retry }
  }

  const isCurrent =
    answer !== null && answer.request === request && answer.attempt === attempt

  return { ...(isCurrent ? answer.state : { status: 'loading' }), retry }
}
