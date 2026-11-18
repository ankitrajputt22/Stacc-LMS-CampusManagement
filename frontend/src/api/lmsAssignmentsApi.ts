import type {
  LmsAssignmentInput,
  LmsCourseAssignment,
  LmsCourseAssignmentDetail,
  LmsFacultyAssignment,
  LmsFacultyAssignmentOverview,
} from '../types/lms'
import { apiClient } from './apiClient'

export async function getCourseAssignments(
  lmsCourseId: number,
  signal?: AbortSignal,
): Promise<LmsCourseAssignment[]> {
  const response = await apiClient.get<LmsCourseAssignment[]>(`/lms/courses/${lmsCourseId}/assignments`, {
    signal,
  })
  return response.data
}

export async function getCourseAssignment(
  lmsCourseId: number,
  assignmentId: number,
  signal?: AbortSignal,
): Promise<LmsCourseAssignmentDetail> {
  const response = await apiClient.get<LmsCourseAssignmentDetail>(
    `/lms/courses/${lmsCourseId}/assignments/${assignmentId}`,
    { signal },
  )
  return response.data
}

export async function getFacultyCourseAssignments(
  lmsCourseId: number,
  signal?: AbortSignal,
): Promise<LmsFacultyAssignment[]> {
  const response = await apiClient.get<LmsFacultyAssignment[]>(
    `/lms/faculty/courses/${lmsCourseId}/assignments`,
    { signal },
  )
  return response.data
}

export async function getMyFacultyAssignments(signal?: AbortSignal): Promise<LmsFacultyAssignmentOverview[]> {
  const response = await apiClient.get<LmsFacultyAssignmentOverview[]>('/lms/faculty/my-assignments', { signal })
  return response.data
}

export async function createAssignment(
  lmsCourseId: number,
  input: LmsAssignmentInput,
): Promise<LmsFacultyAssignment> {
  const response = await apiClient.post<LmsFacultyAssignment>(
    `/lms/faculty/courses/${lmsCourseId}/assignments`,
    input,
  )
  return response.data
}

export async function updateAssignment(
  assignmentId: number,
  input: LmsAssignmentInput,
): Promise<LmsFacultyAssignment> {
  const response = await apiClient.put<LmsFacultyAssignment>(`/lms/faculty/assignments/${assignmentId}`, input)
  return response.data
}

export async function publishAssignment(assignmentId: number): Promise<LmsFacultyAssignment> {
  const response = await apiClient.post<LmsFacultyAssignment>(`/lms/faculty/assignments/${assignmentId}/publish`)
  return response.data
}

export async function archiveAssignment(assignmentId: number): Promise<LmsFacultyAssignment> {
  const response = await apiClient.post<LmsFacultyAssignment>(`/lms/faculty/assignments/${assignmentId}/archive`)
  return response.data
}

export type SubmissionRulesInput = {
  availableFrom: string | null
  availableUntil: string | null
  allowLateSubmissions: boolean
  maxAttempts: number | null
}

export async function setSubmissionRules(assignmentId: number, input: SubmissionRulesInput): Promise<LmsFacultyAssignment> {
  return (await apiClient.put<LmsFacultyAssignment>(`/lms/faculty/assignments/${assignmentId}/submission-rules`, input)).data
}
