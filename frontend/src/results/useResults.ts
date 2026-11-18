import { getCourseResults, getMyResultCourses, getMyResults } from '../api/resultsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'

export function useMyResultCourses() {
  return useApiQuery(queryKeys.results.myCourses(), getMyResultCourses)
}

export function useCourseResults(courseOfferingId: number | null) {
  return useApiQuery(courseOfferingId === null ? null : queryKeys.results.course(courseOfferingId), (signal) =>
    getCourseResults(courseOfferingId ?? 0, signal),
  )
}

export function useMyResults() {
  return useApiQuery(queryKeys.results.mine(), getMyResults)
}
