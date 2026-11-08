import { getFacultyCourseContent } from '../api/lmsContentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'

/**
 * One course the signed-in faculty member teaches, with its content, from the shared query cache, so the course's
 * pages and its navigation share one copy. With no valid ID nothing is sent. The backend checks access every time.
 */
export function useFacultyCourse(lmsCourseId: number | null) {
  return useApiQuery(lmsCourseId === null ? null : queryKeys.lms.facultyCourseContent(lmsCourseId), (signal) =>
    getFacultyCourseContent(lmsCourseId as number, signal),
  )
}
