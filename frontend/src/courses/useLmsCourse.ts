import { getLmsCourse } from '../api/lmsCoursesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'

/**
 * One LMS course for the signed-in student, from the shared query cache. With no valid ID nothing is sent and the
 * course is simply unavailable. The backend decides access again on every request.
 */
export function useLmsCourse(lmsCourseId: number | null) {
  return useApiQuery(lmsCourseId === null ? null : queryKeys.lms.course(lmsCourseId), (signal) =>
    getLmsCourse(lmsCourseId as number, signal),
  )
}
