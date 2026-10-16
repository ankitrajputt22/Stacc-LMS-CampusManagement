import type {
  LmsAssignmentInput,
  LmsCourseAssignment,
  LmsCourseAssignmentDetail,
  LmsFacultyAssignment,
  LmsFacultyAssignmentOverview,
} from '../types/lms'
import { apiClient } from './apiClient'

// The client's base URL already ends in /api. The signed-in account is the one in the access token;
// no account, student, or faculty ID is ever sent from here.

/** The published assignments of a course the signed-in student may use, earliest deadline first. */
export async function getCourseAssignments(
  lmsCourseId: number,
  signal?: AbortSignal,
): Promise<LmsCourseAssignment[]> {
  const response = await apiClient.get<LmsCourseAssignment[]>(`/lms/courses/${lmsCourseId}/assignments`, {
    signal,
  })
  return response.data
}

/** One published assignment of a course the signed-in student may use. */
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

/** The draft and published assignments of a course the signed-in faculty member teaches, earliest deadline first. */
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

/** The draft and published assignments of every course the signed-in faculty member teaches. */
export async function getMyFacultyAssignments(signal?: AbortSignal): Promise<LmsFacultyAssignmentOverview[]> {
  const response = await apiClient.get<LmsFacultyAssignmentOverview[]>('/lms/faculty/my-assignments', { signal })
  return response.data
}

/** Adds a draft assignment to the course. */
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

/** Changes the definition. A published assignment stays published and keeps its publication date. */
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

/** Archives the assignment. It is kept, but students and the faculty lists no longer show it. */
export async function archiveAssignment(assignmentId: number): Promise<LmsFacultyAssignment> {
  const response = await apiClient.post<LmsFacultyAssignment>(`/lms/faculty/assignments/${assignmentId}/archive`)
  return response.data
}
