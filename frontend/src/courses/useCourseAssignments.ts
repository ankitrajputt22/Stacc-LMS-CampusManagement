import { useMemo } from 'react'
import { getCourseAssignments } from '../api/lmsAssignmentsApi'
import { useApiRequest } from '../api/useApiRequest'

/**
 * The published assignments of one course for the signed-in student. With no valid ID nothing is sent.
 * The backend decides access again on every request and sends published assignments only.
 */
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
