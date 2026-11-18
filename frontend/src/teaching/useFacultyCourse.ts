import { getFacultyCourseContent } from '../api/lmsContentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'

export function useFacultyCourse(lmsCourseId: number | null) {
  return useApiQuery(lmsCourseId === null ? null : queryKeys.lms.facultyCourseContent(lmsCourseId), (signal) =>
    getFacultyCourseContent(lmsCourseId as number, signal),
  )
}
