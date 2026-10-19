import type { StudentDashboard } from '../types/dashboard'
import { apiClient } from './apiClient'

/**
 * The signed-in student's dashboard. The client's base URL already ends in /api, so this is
 * GET /api/dashboard/student. The student is the one in the access token; nothing about them is sent from here.
 */
export async function getStudentDashboard(signal?: AbortSignal): Promise<StudentDashboard> {
  const response = await apiClient.get<StudentDashboard>('/dashboard/student', { signal })
  return response.data
}
