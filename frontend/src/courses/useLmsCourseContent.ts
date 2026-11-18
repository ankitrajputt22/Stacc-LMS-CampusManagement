import { useMemo } from 'react'
import { getLmsCourseContent } from '../api/lmsContentApi'
import { useApiRequest } from '../api/useApiRequest'

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
