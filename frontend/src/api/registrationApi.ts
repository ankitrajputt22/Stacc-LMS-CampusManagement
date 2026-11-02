import type { RegisteredCourse, RegistrationOverview, RegistrationResult } from '../types/registration'
import { apiClient } from './apiClient'

// The student is the one in the access token; no student, profile, or account ID is ever sent from here.

/** The signed-in student's registration page: open semesters, their offerings, and the student's courses. */
export async function getMyRegistration(signal?: AbortSignal): Promise<RegistrationOverview> {
  const response = await apiClient.get<RegistrationOverview>('/students/me/course-registration', { signal })
  return response.data
}

/** Registers for every chosen offering, or for none when one cannot be taken. */
export async function registerForCourses(courseOfferingIds: number[]): Promise<RegistrationResult> {
  const response = await apiClient.post<RegistrationResult>('/students/me/course-registration', { courseOfferingIds })
  return response.data
}

/** Drops one of the student's own courses before the drop deadline. */
export async function dropCourse(courseEnrollmentId: number): Promise<RegisteredCourse> {
  const response = await apiClient.post<RegisteredCourse>(`/students/me/course-enrollments/${courseEnrollmentId}/drop`)
  return response.data
}
