import { useMemo } from 'react'
import { getLmsCourseContent } from '../api/lmsContentApi'
import { useApiRequest } from '../api/useApiRequest'

/**
 * The published content of one LMS course for the signed-in student. With no valid ID nothing is
 * sent. The backend decides access again on every request and sends published content only.
 */
export function useLmsCourseContent(lmsCourseId: number | null) {
  const request = useMemo(
    () =>
      lmsCourseId === null
        ? null
        : (signal: AbortSignal) => getLmsCourseContent(lmsCourseId, signal),
    [lmsCourseId],
  )

  return useApiRequest(request)
}
