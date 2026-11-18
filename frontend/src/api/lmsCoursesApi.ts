import type { LmsCourseDetail, LmsCourseSummary } from '../types/lms'
import { apiClient } from './apiClient'

export async function getMyLmsCourses(
  signal?: AbortSignal,
): Promise<LmsCourseSummary[]> {
  const response = await apiClient.get<LmsCourseSummary[]>('/lms/my-courses', {
    signal,
  })

  return response.data
}

export async function getLmsCourse(
  lmsCourseId: number,
  signal?: AbortSignal,
): Promise<LmsCourseDetail> {
  const response = await apiClient.get<LmsCourseDetail>(
    `/lms/courses/${lmsCourseId}`,
    { signal },
  )

  return response.data
}
