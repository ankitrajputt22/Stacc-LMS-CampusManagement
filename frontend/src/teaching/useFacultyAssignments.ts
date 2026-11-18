import { useMemo } from 'react'
import { getFacultyCourseAssignments } from '../api/lmsAssignmentsApi'
import { useApiRequest } from '../api/useApiRequest'

export function useFacultyAssignments(lmsCourseId: number) {
  const request = useMemo(
    () => (signal: AbortSignal) => getFacultyCourseAssignments(lmsCourseId, signal),
    [lmsCourseId],
  )

  return useApiRequest(request)
}
