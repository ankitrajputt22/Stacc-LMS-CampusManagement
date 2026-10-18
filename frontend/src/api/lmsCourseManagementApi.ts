import type { LmsCourseManagement, LmsManagedCourse } from '../types/lms'
import { apiClient } from './apiClient'

// The client's base URL already ends in /api. Every route needs the faculty role, FACULTY_COURSE_VIEW, and
// LMS_COURSE_MANAGE; which offerings a faculty member manages is decided on the backend from official teaching
// assignments, for the account in the access token. Nothing about the faculty member is sent from here.

/** Every course offering the faculty member teaches, with its LMS course when it has one, in course code order. */
export async function getManagedTeachingCourses(signal?: AbortSignal) {
  const response = await apiClient.get<LmsManagedCourse[]>('/lms/faculty/managed-courses', { signal })
  return response.data
}

/** Creates the draft LMS course of an offering and gives its current students LMS membership. */
export async function createLmsCourse(courseOfferingId: number) {
  const response = await apiClient.post<LmsCourseManagement>(`/lms/faculty/course-offerings/${courseOfferingId}/lms-course`)
  return response.data
}

export async function syncStudentMemberships(lmsCourseId: number) {
  const response = await apiClient.post<LmsCourseManagement>(`/lms/faculty/courses/${lmsCourseId}/sync-student-memberships`)
  return response.data
}

export async function publishLmsCourse(lmsCourseId: number) {
  const response = await apiClient.post<LmsCourseManagement>(`/lms/faculty/courses/${lmsCourseId}/publish`)
  return response.data
}

export async function archiveLmsCourse(lmsCourseId: number) {
  const response = await apiClient.post<LmsCourseManagement>(`/lms/faculty/courses/${lmsCourseId}/archive`)
  return response.data
}
