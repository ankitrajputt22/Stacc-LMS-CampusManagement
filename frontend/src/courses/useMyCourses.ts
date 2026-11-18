import { getMyLmsCourses } from '../api/lmsCoursesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'

export function useMyCourses() {
  return useApiQuery(queryKeys.lms.myCourses(), getMyLmsCourses)
}
