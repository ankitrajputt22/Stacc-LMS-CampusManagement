import { useMemo } from 'react'
import { getLmsCourse } from '../api/lmsCoursesApi'
import { useApiRequest } from '../api/useApiRequest'

/**
 * One LMS course for the signed-in student. With no valid ID nothing is sent and the course is
 * simply unavailable. The backend decides access again on every request.
 */
export function useLmsCourse(lmsCourseId: number | null) {
  const request = useMemo(
    () =>
      lmsCourseId === null
        ? null
        : (signal: AbortSignal) => getLmsCourse(lmsCourseId, signal),
    [lmsCourseId],
  )

  return useApiRequest(request)
}
