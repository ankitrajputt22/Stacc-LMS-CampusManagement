import type { LmsSubmissionAttempt, LmsSubmissionHistory } from '../types/lms'
import { apiClient } from './apiClient'
import { FILE_TIMEOUT_MS } from './lmsContentApi'

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

export async function downloadSubmissionAttachment(attachmentId: number): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/lms/submission-attachments/${attachmentId}/download`, {
    responseType: 'blob',
    timeout: FILE_TIMEOUT_MS,
  })
  return response.data
}
