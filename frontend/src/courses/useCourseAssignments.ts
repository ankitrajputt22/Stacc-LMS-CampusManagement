import { useMemo } from 'react'
import { getCourseAssignments } from '../api/lmsAssignmentsApi'
import { useApiRequest } from '../api/useApiRequest'

export function useCourseAssignments(lmsCourseId: number | null) {
  const request = useMemo(
    () =>
      lmsCourseId === null
        ? null
        : (signal: AbortSignal) => getCourseAssignments(lmsCourseId, signal),
    [lmsCourseId],
  )

  return useApiRequest(request)
}
