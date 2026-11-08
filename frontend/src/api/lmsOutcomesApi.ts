import type { BloomLevel, LmsFacultyAssignment, LmsOutcome } from '../types/lms'
import { apiClient } from './apiClient'

// The signed-in account is the one in the session; the backend checks access to the course on every request.

/** The active outcomes of a course the signed-in student may use. */
export async function getCourseOutcomes(lmsCourseId: number, signal?: AbortSignal): Promise<LmsOutcome[]> {
  return (await apiClient.get<LmsOutcome[]>(`/lms/courses/${lmsCourseId}/outcomes`, { signal })).data
}

/** Every outcome of a course the signed-in faculty member teaches, archived ones included. */
export async function getFacultyOutcomes(lmsCourseId: number, signal?: AbortSignal): Promise<LmsOutcome[]> {
  return (await apiClient.get<LmsOutcome[]>(`/lms/faculty/courses/${lmsCourseId}/outcomes`, { signal })).data
}

/** Adds the course's next outcome; the server gives its code. */
export async function addOutcome(lmsCourseId: number, description: string): Promise<LmsOutcome> {
  return (await apiClient.post<LmsOutcome>(`/lms/faculty/courses/${lmsCourseId}/outcomes`, { description })).data
}

export async function describeOutcome(outcomeId: number, description: string): Promise<LmsOutcome> {
  return (await apiClient.put<LmsOutcome>(`/lms/faculty/outcomes/${outcomeId}`, { description })).data
}

export async function archiveOutcome(outcomeId: number): Promise<LmsOutcome> {
  return (await apiClient.post<LmsOutcome>(`/lms/faculty/outcomes/${outcomeId}/archive`)).data
}

/** Sets an assignment's Bloom level (or none) and the outcomes it assesses. */
export async function alignAssignment(
  assignmentId: number,
  input: { bloomLevel: BloomLevel | null; outcomeIds: number[] },
): Promise<LmsFacultyAssignment> {
  return (await apiClient.put<LmsFacultyAssignment>(`/lms/faculty/assignments/${assignmentId}/alignment`, input)).data
}
