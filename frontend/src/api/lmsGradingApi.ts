import type {
  LmsFacultySubmissionDetail,
  LmsFacultySubmissionSummary,
  LmsGradeInput,
  LmsSubmissionGrade,
} from '../types/lms'
import { apiClient } from './apiClient'
import { FILE_TIMEOUT_MS } from './lmsContentApi'

// The client's base URL already ends in /api. The faculty member is the one in the access token; no
// faculty, profile, or student ID is ever sent from here.

/** The students' submissions for an assignment, each summed up by its latest attempt, newest first. */
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

/** One student's submission with every attempt, newest first. */
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

/** Saves the attempt's grade, or revises it. The student sees it at once. */
export async function gradeSubmissionAttempt(attemptId: number, input: LmsGradeInput): Promise<LmsSubmissionGrade> {
  const response = await apiClient.put<LmsSubmissionGrade>(`/lms/faculty/submission-attempts/${attemptId}/grade`, input)
  return response.data
}

/** Downloads a student's submitted file for review through the API, never from storage directly. */
export async function downloadFacultySubmissionAttachment(attachmentId: number): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/lms/faculty/submission-attachments/${attachmentId}/download`, {
    responseType: 'blob',
    timeout: FILE_TIMEOUT_MS,
  })
  return response.data
}
