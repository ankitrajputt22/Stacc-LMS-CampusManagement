import { useCallback, useEffect, useState } from 'react'
import { getHttpStatus } from '../api/apiError'
import { getMyLmsCourses } from '../api/lmsCoursesApi'
import type { LmsCourseSummary } from '../types/lms'

export type MyCoursesState =
  | { status: 'loading' }
  | { status: 'loaded'; courses: LmsCourseSummary[] }
  /** The backend refused (403). The user stays signed in. */
  | { status: 'denied' }
  | { status: 'failed' }

/**
 * Loads the signed-in student's LMS courses once, and again when retry() is called. A request
 * still running when the page closes or a retry starts is cancelled, so an old answer never
 * replaces a newer one.
 */
export function useMyCourses(): MyCoursesState & { retry: () => void } {
  const [state, setState] = useState<MyCoursesState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    getMyLmsCourses(controller.signal)
      .then((courses) => setState({ status: 'loaded', courses }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return
        }

        const status = getHttpStatus(error)
        if (status === 401) {
          // The session has ended. The shared client has already signed the user out, and the
          // sign-in page is on its way, so there is nothing to show here.
          return
        }

        setState({ status: status === 403 ? 'denied' : 'failed' })
      })

    return () => controller.abort()
  }, [attempt])

  const retry = useCallback(() => {
    setState({ status: 'loading' })
    setAttempt((previous) => previous + 1)
  }, [])

  return { ...state, retry }
}
