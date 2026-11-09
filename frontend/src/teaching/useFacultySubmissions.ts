import { useMemo } from 'react'
import { getFacultyAssignmentSubmissions, getFacultySubmissionDetail } from '../api/lmsGradingApi'
import { useApiRequest } from '../api/useApiRequest'

/**
 * The students' submissions for a published assignment the signed-in faculty member teaches. refresh()
 * reloads them quietly after a grade is saved. The backend checks access and the grading permission on
 * every request; a null ID sends nothing.
 */
export function useFacultySubmissions(assignmentId: number | null) {
  const request = useMemo(
    () => (assignmentId === null ? null : (signal: AbortSignal) => getFacultyAssignmentSubmissions(assignmentId, signal)),
    [assignmentId],
  )

  return useApiRequest(request)
}

/** One student's submission with every attempt, for review. refresh() reloads it quietly after grading. */
export function useFacultySubmissionDetail(assignmentId: number | null, submissionId: number | null) {
  const request = useMemo(
    () =>
      assignmentId === null || submissionId === null
        ? null
        : (signal: AbortSignal) => getFacultySubmissionDetail(assignmentId, submissionId, signal),
    [assignmentId, submissionId],
  )

  return useApiRequest(request)
}
