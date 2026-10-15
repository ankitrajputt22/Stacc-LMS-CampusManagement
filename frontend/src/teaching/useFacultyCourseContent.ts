import { useMemo } from 'react'
import { getFacultyCourseContent } from '../api/lmsContentApi'
import { useApiRequest } from '../api/useApiRequest'

/**
 * A course the signed-in faculty member teaches, with its content. refresh() reloads it quietly
 * after a change. The backend decides access from official teaching assignments on every request.
 */
export function useFacultyCourseContent(lmsCourseId: number | null) {
  const request = useMemo(
    () =>
      lmsCourseId === null
        ? null
        : (signal: AbortSignal) => getFacultyCourseContent(lmsCourseId, signal),
    [lmsCourseId],
  )

  return useApiRequest(request)
}
