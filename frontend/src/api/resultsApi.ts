import type {
  CourseResultInput,
  CourseResultRoster,
  FacultyResult,
  ResultCourseOffering,
  StudentResults,
} from '../types/results'
import { apiClient } from './apiClient'

// The client's base URL already ends in /api. The faculty member or student is the one in the access token; no
// faculty, student, or profile ID is ever sent from here.

/** The course offerings the signed-in faculty member records results for. */
export async function getMyResultCourses(signal?: AbortSignal): Promise<ResultCourseOffering[]> {
  const response = await apiClient.get<ResultCourseOffering[]>('/results/faculty/my-courses', { signal })
  return response.data
}

/** A course offering with every enrollment and its result. */
export async function getCourseResults(courseOfferingId: number, signal?: AbortSignal): Promise<CourseResultRoster> {
  const response = await apiClient.get<CourseResultRoster>(`/results/faculty/course-offerings/${courseOfferingId}`, {
    signal,
  })
  return response.data
}

/** Saves an enrollment's result: a new draft, an edited draft, or a correction that stays published. */
export async function saveCourseResult(
  courseOfferingId: number,
  courseEnrollmentId: number,
  input: CourseResultInput,
): Promise<FacultyResult> {
  const response = await apiClient.put<FacultyResult>(
    `/results/faculty/course-offerings/${courseOfferingId}/enrollments/${courseEnrollmentId}`,
    input,
  )
  return response.data
}

/** Publishes an enrollment's saved result. The student sees it at once. */
export async function publishCourseResult(courseOfferingId: number, courseEnrollmentId: number): Promise<FacultyResult> {
  const response = await apiClient.post<FacultyResult>(
    `/results/faculty/course-offerings/${courseOfferingId}/enrollments/${courseEnrollmentId}/publish`,
  )
  return response.data
}

/** The signed-in student's own published results. */
export async function getMyResults(signal?: AbortSignal): Promise<StudentResults> {
  const response = await apiClient.get<StudentResults>('/results/student/me', { signal })
  return response.data
}
