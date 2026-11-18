import { useMemo } from 'react'
import { getCourseAssignment } from '../api/lmsAssignmentsApi'
import { useApiRequest } from '../api/useApiRequest'

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
