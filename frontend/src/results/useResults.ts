import { useMemo } from 'react'
import { getCourseResults, getMyResultCourses, getMyResults } from '../api/resultsApi'
import { useApiRequest } from '../api/useApiRequest'

/** The course offerings the signed-in faculty member teaches. The backend decides from teaching assignments. */
export function useMyResultCourses() {
  return useApiRequest(getMyResultCourses)
}

/** A course offering's enrollments and their results. A null ID sends nothing. */
export function useCourseResults(courseOfferingId: number | null) {
  const request = useMemo(
    () => (courseOfferingId === null ? null : (signal: AbortSignal) => getCourseResults(courseOfferingId, signal)),
    [courseOfferingId],
  )
  return useApiRequest(request)
}

/** The signed-in student's own published results. */
export function useMyResults() {
  return useApiRequest(getMyResults)
}
