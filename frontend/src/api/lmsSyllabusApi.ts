import type { LmsCourseSyllabus, LmsFacultySyllabus } from '../types/lms'
import { apiClient } from './apiClient'

export async function getCourseSyllabus(lmsCourseId: number, signal?: AbortSignal): Promise<LmsCourseSyllabus> {
  return (await apiClient.get<LmsCourseSyllabus>(`/lms/courses/${lmsCourseId}/syllabus`, { signal })).data
}

export async function getFacultySyllabus(lmsCourseId: number, signal?: AbortSignal): Promise<LmsFacultySyllabus> {
  return (await apiClient.get<LmsFacultySyllabus>(`/lms/faculty/courses/${lmsCourseId}/syllabus`, { signal })).data
}

export async function saveSyllabusDraft(lmsCourseId: number, content: string): Promise<LmsFacultySyllabus> {
  return (await apiClient.put<LmsFacultySyllabus>(`/lms/faculty/courses/${lmsCourseId}/syllabus`, { content })).data
}

export async function publishSyllabus(lmsCourseId: number): Promise<LmsFacultySyllabus> {
  return (await apiClient.post<LmsFacultySyllabus>(`/lms/faculty/courses/${lmsCourseId}/syllabus/publish`)).data
}
