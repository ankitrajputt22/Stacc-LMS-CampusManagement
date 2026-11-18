import type { LmsCourseManagement, LmsManagedCourse } from '../types/lms'
import { apiClient } from './apiClient'
import type { LmsCourseHomeView } from '../types/lms'

export async function getManagedTeachingCourses(signal?: AbortSignal) {
  const response = await apiClient.get<LmsManagedCourse[]>('/lms/faculty/managed-courses', { signal })
  return response.data
}

export async function createLmsCourse(courseOfferingId: number) {
  const response = await apiClient.post<LmsCourseManagement>(`/lms/faculty/course-offerings/${courseOfferingId}/lms-course`)
  return response.data
}

export async function publishLmsCourse(lmsCourseId: number) {
  const response = await apiClient.post<LmsCourseManagement>(`/lms/faculty/courses/${lmsCourseId}/publish`)
  return response.data
}

export async function archiveLmsCourse(lmsCourseId: number) {
  const response = await apiClient.post<LmsCourseManagement>(`/lms/faculty/courses/${lmsCourseId}/archive`)
  return response.data
}

export async function changeCourseHome(
  lmsCourseId: number,
  input: { homeView: LmsCourseHomeView; welcomeMessage: string | null },
): Promise<{ homeView: LmsCourseHomeView; welcomeMessage: string | null }> {
  return (await apiClient.put<{ homeView: LmsCourseHomeView; welcomeMessage: string | null }>(
    `/lms/faculty/courses/${lmsCourseId}/home`,
    input,
  )).data
}


export type LmsCopySource = {
  lmsCourseId: number
  courseCode: string
  academicSessionCode: string
  semesterNumber: number
  programCode: string
  sectionCode: string | null
  lmsCourseStatus: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
}

export type LmsCopyResult = { modules: number; pages: number; files: number; syllabus: boolean }

export async function getCopySources(lmsCourseId: number, signal?: AbortSignal): Promise<LmsCopySource[]> {
  return (await apiClient.get<LmsCopySource[]>(`/lms/faculty/courses/${lmsCourseId}/copy-sources`, { signal })).data
}

export async function copyCourseContent(lmsCourseId: number, sourceLmsCourseId: number): Promise<LmsCopyResult> {
  return (await apiClient.post<LmsCopyResult>(`/lms/faculty/courses/${lmsCourseId}/copy`, { sourceLmsCourseId })).data
}
