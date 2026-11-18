import type {
  LmsFacultySubmissionDetail,
  LmsFacultySubmissionSummary,
  LmsGradeInput,
  LmsSubmissionGrade,
} from '../types/lms'
import { apiClient } from './apiClient'
import { FILE_TIMEOUT_MS } from './lmsContentApi'

export async function getFacultyAssignmentSubmissions(
  assignmentId: number,
  signal?: AbortSignal,
): Promise<LmsFacultySubmissionSummary[]> {
  const response = await apiClient.get<LmsFacultySubmissionSummary[]>(
    `/lms/faculty/assignments/${assignmentId}/submissions`,
    { signal },
  )
  return response.data
}

export async function getFacultySubmissionDetail(
  assignmentId: number,
  submissionId: number,
  signal?: AbortSignal,
): Promise<LmsFacultySubmissionDetail> {
  const response = await apiClient.get<LmsFacultySubmissionDetail>(
    `/lms/faculty/assignments/${assignmentId}/submissions/${submissionId}`,
    { signal },
  )
  return response.data
}

export async function gradeSubmissionAttempt(attemptId: number, input: LmsGradeInput): Promise<LmsSubmissionGrade> {
  const response = await apiClient.put<LmsSubmissionGrade>(`/lms/faculty/submission-attempts/${attemptId}/grade`, input)
  return response.data
}

export async function downloadFacultySubmissionAttachment(attachmentId: number): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/lms/faculty/submission-attachments/${attachmentId}/download`, {
    responseType: 'blob',
    timeout: FILE_TIMEOUT_MS,
  })
  return response.data
}
