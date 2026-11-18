import { useMemo } from 'react'
import { getMyAssignmentSubmissions } from '../api/lmsSubmissionsApi'
import { useApiRequest } from '../api/useApiRequest'

export function useMySubmissions(lmsCourseId: number, assignmentId: number) {
  const request = useMemo(
    () => (signal: AbortSignal) => getMyAssignmentSubmissions(lmsCourseId, assignmentId, signal),
    [lmsCourseId, assignmentId],
  )

  return useApiRequest(request)
}
