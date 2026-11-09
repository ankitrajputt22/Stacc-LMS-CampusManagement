import { useMemo } from 'react'
import { getCourseAssignment } from '../api/lmsAssignmentsApi'
import { useApiRequest } from '../api/useApiRequest'

/**
 * One published assignment for the signed-in student. With an ID that cannot be valid nothing is sent,
 * and the state is notFound. The backend checks access to the course and the assignment every time.
 */
export function useCourseAssignment(lmsCourseId: number | null, assignmentId: number | null) {
  const request = useMemo(
    () =>
      lmsCourseId === null || assignmentId === null
        ? null
        : (signal: AbortSignal) => getCourseAssignment(lmsCourseId, assignmentId, signal),
    [lmsCourseId, assignmentId],
  )

  return useApiRequest(request)
}
