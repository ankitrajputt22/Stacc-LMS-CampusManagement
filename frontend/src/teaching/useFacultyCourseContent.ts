import { useMemo } from 'react'
import { getFacultyCourseContent } from '../api/lmsContentApi'
import { useApiRequest } from '../api/useApiRequest'

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
