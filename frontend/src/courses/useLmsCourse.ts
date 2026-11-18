import { getLmsCourse } from '../api/lmsCoursesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'

export function useLmsCourse(lmsCourseId: number | null) {
  return useApiQuery(lmsCourseId === null ? null : queryKeys.lms.course(lmsCourseId), (signal) =>
    getLmsCourse(lmsCourseId as number, signal),
  )
}
