import { getCourseResults, getMyResultCourses, getMyResults } from '../api/resultsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'

/** The course offerings the signed-in faculty member teaches. The backend decides from teaching assignments. */
export function useMyResultCourses() {
  return useApiQuery(queryKeys.results.myCourses(), getMyResultCourses)
}

/** A course offering's enrollments and their results. A null ID sends nothing. */
export function useCourseResults(courseOfferingId: number | null) {
  return useApiQuery(courseOfferingId === null ? null : queryKeys.results.course(courseOfferingId), (signal) =>
    getCourseResults(courseOfferingId ?? 0, signal),
  )
}

/** The signed-in student's own published results. */
export function useMyResults() {
  return useApiQuery(queryKeys.results.mine(), getMyResults)
}
