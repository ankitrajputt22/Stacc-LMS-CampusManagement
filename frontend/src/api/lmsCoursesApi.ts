import type { LmsCourseSummary } from '../types/lms'
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
