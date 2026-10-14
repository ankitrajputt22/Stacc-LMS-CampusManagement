import { getMyLmsCourses } from '../api/lmsCoursesApi'
import { useApiRequest } from '../api/useApiRequest'

/** The signed-in student's LMS courses, loaded once and again on retry(). */
export function useMyCourses() {
  return useApiRequest(getMyLmsCourses)
}
