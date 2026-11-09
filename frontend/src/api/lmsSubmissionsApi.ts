import type { LmsSubmissionAttempt, LmsSubmissionHistory } from '../types/lms'
import { apiClient } from './apiClient'
import { FILE_TIMEOUT_MS } from './lmsContentApi'

// The client's base URL already ends in /api. The student is the one in the access token; no account,
// student, or enrollment ID is ever sent from here.

/** The signed-in student's own attempts for an assignment, newest first. Empty before the first one. */
export async function getMyAssignmentSubmissions(
  lmsCourseId: number,
  assignmentId: number,
  signal?: AbortSignal,
): Promise<LmsSubmissionHistory> {
  const response = await apiClient.get<LmsSubmissionHistory>(
    `/lms/courses/${lmsCourseId}/assignments/${assignmentId}/submissions/me`,
    { signal },
  )
  return response.data
}

/**
 * Hands in a new attempt as multipart/form-data: the text, if any, and each file as its own "files" part.
 * The browser sets the multipart header itself; no Content-Type is set here. Earlier attempts are kept.
 */
export async function submitAssignment(
  lmsCourseId: number,
  assignmentId: number,
  textContent: string,
  files: File[],
): Promise<LmsSubmissionAttempt> {
  const form = new FormData()
  if (textContent.trim() !== '') {
    form.append('textContent', textContent)
  }
  for (const file of files) {
    form.append('files', file)
  }
  const response = await apiClient.post<LmsSubmissionAttempt>(
    `/lms/courses/${lmsCourseId}/assignments/${assignmentId}/submissions`,
    form,
    { timeout: FILE_TIMEOUT_MS },
  )
  return response.data
}

/** Downloads one of the student's own submission files through the API, never from storage directly. */
export async function downloadSubmissionAttachment(attachmentId: number): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/lms/submission-attachments/${attachmentId}/download`, {
    responseType: 'blob',
    timeout: FILE_TIMEOUT_MS,
  })
  return response.data
}
