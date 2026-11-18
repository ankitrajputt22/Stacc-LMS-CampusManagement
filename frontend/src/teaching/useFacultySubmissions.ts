import { useMemo } from 'react'
import { getFacultyAssignmentSubmissions, getFacultySubmissionDetail } from '../api/lmsGradingApi'
import { useApiRequest } from '../api/useApiRequest'

export function useFacultySubmissions(assignmentId: number | null) {
  const request = useMemo(
    () => (assignmentId === null ? null : (signal: AbortSignal) => getFacultyAssignmentSubmissions(assignmentId, signal)),
    [assignmentId],
  )

  return useApiRequest(request)
}

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
