import type { AdminDashboard, FacultyDashboard, StudentDashboard } from '../types/dashboard'
import { apiClient } from './apiClient'

export async function getStudentDashboard(signal?: AbortSignal): Promise<StudentDashboard> {
  const response = await apiClient.get<StudentDashboard>('/dashboard/student', { signal })
  return response.data
}

export async function getFacultyDashboard(signal?: AbortSignal): Promise<FacultyDashboard> {
  const response = await apiClient.get<FacultyDashboard>('/dashboard/faculty', { signal })
  return response.data
}

export async function getAdminDashboard(signal?: AbortSignal): Promise<AdminDashboard> {
  const response = await apiClient.get<AdminDashboard>('/dashboard/admin', { signal })
  return response.data
}
