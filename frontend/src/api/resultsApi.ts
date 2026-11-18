import type {
  CourseResultInput,
  CourseResultRoster,
  FacultyResult,
  ResultCourseOffering,
  StudentResults,
} from '../types/results'
import { apiClient } from './apiClient'

export async function getMyResultCourses(signal?: AbortSignal): Promise<ResultCourseOffering[]> {
  const response = await apiClient.get<ResultCourseOffering[]>('/results/faculty/my-courses', { signal })
  return response.data
}

export async function getCourseResults(courseOfferingId: number, signal?: AbortSignal): Promise<CourseResultRoster> {
  const response = await apiClient.get<CourseResultRoster>(`/results/faculty/course-offerings/${courseOfferingId}`, {
    signal,
  })
  return response.data
}

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

export async function publishCourseResult(courseOfferingId: number, courseEnrollmentId: number): Promise<FacultyResult> {
  const response = await apiClient.post<FacultyResult>(
    `/results/faculty/course-offerings/${courseOfferingId}/enrollments/${courseEnrollmentId}/publish`,
  )
  return response.data
}

export async function getMyResults(signal?: AbortSignal): Promise<StudentResults> {
  const response = await apiClient.get<StudentResults>('/results/student/me', { signal })
  return response.data
}
