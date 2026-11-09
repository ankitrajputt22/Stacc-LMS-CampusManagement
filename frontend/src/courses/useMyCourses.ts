import { getMyLmsCourses } from '../api/lmsCoursesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'

/** The signed-in student's LMS courses, from the shared query cache. */
export function useMyCourses() {
  return useApiQuery(queryKeys.lms.myCourses(), getMyLmsCourses)
}
