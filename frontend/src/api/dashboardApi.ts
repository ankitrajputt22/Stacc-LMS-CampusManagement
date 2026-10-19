import type { AdminDashboard, FacultyDashboard, StudentDashboard } from '../types/dashboard'
import { apiClient } from './apiClient'

/**
 * The signed-in student's dashboard. The client's base URL already ends in /api, so this is
 * GET /api/dashboard/student. The student is the one in the access token; nothing about them is sent from here.
 */
export async function getStudentDashboard(signal?: AbortSignal): Promise<StudentDashboard> {
  const response = await apiClient.get<StudentDashboard>('/dashboard/student', { signal })
  return response.data
}

/**
 * The signed-in faculty member's dashboard, GET /api/dashboard/faculty. The faculty member is the one in the access
 * token; nothing about them is sent from here.
 */
export async function getFacultyDashboard(signal?: AbortSignal): Promise<FacultyDashboard> {
  const response = await apiClient.get<FacultyDashboard>('/dashboard/faculty', { signal })
  return response.data
}

/** The admin dashboard, GET /api/dashboard/admin. It takes no parameters. */
export async function getAdminDashboard(signal?: AbortSignal): Promise<AdminDashboard> {
  const response = await apiClient.get<AdminDashboard>('/dashboard/admin', { signal })
  return response.data
}
