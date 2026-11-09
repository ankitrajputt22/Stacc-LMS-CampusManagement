import { useMemo } from 'react'
import { getFacultyCourseAssignments } from '../api/lmsAssignmentsApi'
import { useApiRequest } from '../api/useApiRequest'

/**
 * The assignments of a course the signed-in faculty member teaches. refresh() reloads them quietly
 * after a change. The backend decides access from official teaching assignments on every request.
 */
export function useFacultyAssignments(lmsCourseId: number) {
  const request = useMemo(
    () => (signal: AbortSignal) => getFacultyCourseAssignments(lmsCourseId, signal),
    [lmsCourseId],
  )

  return useApiRequest(request)
}
