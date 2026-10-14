import { getMyFacultyCourses } from '../api/lmsCoursesApi'
import { useApiRequest } from '../api/useApiRequest'

/** The LMS courses the signed-in faculty member teaches, loaded once and again on retry(). */
export function useMyTeachingCourses() {
  return useApiRequest(getMyFacultyCourses)
}
