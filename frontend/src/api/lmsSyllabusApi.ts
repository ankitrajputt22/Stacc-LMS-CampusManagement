import type { LmsCourseSyllabus, LmsFacultySyllabus } from '../types/lms'
import { apiClient } from './apiClient'

// The signed-in account is the one in the session; the backend checks access to the course on every request.

/** The published syllabus of a course the signed-in student may use. */
export async function getCourseSyllabus(lmsCourseId: number, signal?: AbortSignal): Promise<LmsCourseSyllabus> {
  return (await apiClient.get<LmsCourseSyllabus>(`/lms/courses/${lmsCourseId}/syllabus`, { signal })).data
}

/** The syllabus of a course the signed-in faculty member teaches, draft included. */
export async function getFacultySyllabus(lmsCourseId: number, signal?: AbortSignal): Promise<LmsFacultySyllabus> {
  return (await apiClient.get<LmsFacultySyllabus>(`/lms/faculty/courses/${lmsCourseId}/syllabus`, { signal })).data
}

/** Saves the draft. Students keep reading the published version. */
export async function saveSyllabusDraft(lmsCourseId: number, content: string): Promise<LmsFacultySyllabus> {
  return (await apiClient.put<LmsFacultySyllabus>(`/lms/faculty/courses/${lmsCourseId}/syllabus`, { content })).data
}

/** Makes the saved draft the version students read. */
export async function publishSyllabus(lmsCourseId: number): Promise<LmsFacultySyllabus> {
  return (await apiClient.post<LmsFacultySyllabus>(`/lms/faculty/courses/${lmsCourseId}/syllabus/publish`)).data
}
