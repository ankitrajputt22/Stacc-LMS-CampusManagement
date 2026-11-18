import type { BloomLevel, LmsFacultyAssignment, LmsOutcome } from '../types/lms'
import { apiClient } from './apiClient'

export async function getCourseOutcomes(lmsCourseId: number, signal?: AbortSignal): Promise<LmsOutcome[]> {
  return (await apiClient.get<LmsOutcome[]>(`/lms/courses/${lmsCourseId}/outcomes`, { signal })).data
}

export async function getFacultyOutcomes(lmsCourseId: number, signal?: AbortSignal): Promise<LmsOutcome[]> {
  return (await apiClient.get<LmsOutcome[]>(`/lms/faculty/courses/${lmsCourseId}/outcomes`, { signal })).data
}

export async function addOutcome(lmsCourseId: number, description: string): Promise<LmsOutcome> {
  return (await apiClient.post<LmsOutcome>(`/lms/faculty/courses/${lmsCourseId}/outcomes`, { description })).data
}

export async function describeOutcome(outcomeId: number, description: string): Promise<LmsOutcome> {
  return (await apiClient.put<LmsOutcome>(`/lms/faculty/outcomes/${outcomeId}`, { description })).data
}

export async function archiveOutcome(outcomeId: number): Promise<LmsOutcome> {
  return (await apiClient.post<LmsOutcome>(`/lms/faculty/outcomes/${outcomeId}/archive`)).data
}

export async function alignAssignment(
  assignmentId: number,
  input: { bloomLevel: BloomLevel | null; outcomeIds: number[] },
): Promise<LmsFacultyAssignment> {
  return (await apiClient.put<LmsFacultyAssignment>(`/lms/faculty/assignments/${assignmentId}/alignment`, input)).data
}
