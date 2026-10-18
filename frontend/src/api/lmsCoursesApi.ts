import type { LmsCourseDetail, LmsCourseSummary } from '../types/lms'
import { apiClient } from './apiClient'

/**
 * The LMS courses of the signed-in student, in the order the backend gives them (course code).
 * The student is the one in the access token, which apiClient adds; nothing about the student is
 * sent from here. The backend decides which courses are included.
 */
export async function getMyLmsCourses(
  signal?: AbortSignal,
): Promise<LmsCourseSummary[]> {
  // The client's base URL already ends in /api, so this is GET /api/lms/my-courses.
  const response = await apiClient.get<LmsCourseSummary[]>('/lms/my-courses', {
    signal,
  })

  return response.data
}

/**
 * One LMS course for the signed-in student. Only the course ID is sent; the backend checks again
 * whether this student may open it and answers 404 when not, without saying why.
 */
export async function getLmsCourse(
  lmsCourseId: number,
  signal?: AbortSignal,
): Promise<LmsCourseDetail> {
  // The client's base URL already ends in /api, so this is GET /api/lms/courses/{lmsCourseId}.
  const response = await apiClient.get<LmsCourseDetail>(
    `/lms/courses/${lmsCourseId}`,
    { signal },
  )

  return response.data
}
