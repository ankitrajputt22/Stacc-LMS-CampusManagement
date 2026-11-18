import type { RegisteredCourse, RegistrationOverview, RegistrationResult } from '../types/registration'
import { apiClient } from './apiClient'

export async function getMyRegistration(signal?: AbortSignal): Promise<RegistrationOverview> {
  const response = await apiClient.get<RegistrationOverview>('/students/me/course-registration', { signal })
  return response.data
}

export async function registerForCourses(courseOfferingIds: number[]): Promise<RegistrationResult> {
  const response = await apiClient.post<RegistrationResult>('/students/me/course-registration', { courseOfferingIds })
  return response.data
}

export async function dropCourse(courseEnrollmentId: number): Promise<RegisteredCourse> {
  const response = await apiClient.post<RegisteredCourse>(`/students/me/course-enrollments/${courseEnrollmentId}/drop`)
  return response.data
}
