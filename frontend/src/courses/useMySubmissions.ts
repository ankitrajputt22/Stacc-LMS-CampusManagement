import { useMemo } from 'react'
import { getMyAssignmentSubmissions } from '../api/lmsSubmissionsApi'
import { useApiRequest } from '../api/useApiRequest'

/**
 * The signed-in student's own attempts for one assignment. refresh() reloads them quietly after a new
 * attempt. The backend finds the student's enrollment itself and checks access on every request.
 */
export function useMySubmissions(lmsCourseId: number, assignmentId: number) {
  const request = useMemo(
    () => (signal: AbortSignal) => getMyAssignmentSubmissions(lmsCourseId, assignmentId, signal),
    [lmsCourseId, assignmentId],
  )

  return useApiRequest(request)
}
