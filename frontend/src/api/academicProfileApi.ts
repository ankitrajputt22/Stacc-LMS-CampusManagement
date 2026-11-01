import type { ActiveStatus } from '../types/admin'
import type { FacultyProfileSummary, MyAcademicProfile, StudentProfileSummary } from '../types/academicProfile'
import { apiClient } from './apiClient'

const PROFILES = '/admin/academic/profiles'

/** The signed-in person's own academic profile; the backend takes the account from the session. */
export async function getMyAcademicProfile(signal?: AbortSignal) {
  const response = await apiClient.get<MyAcademicProfile>('/academic/me', { signal })
  return response.data
}

/** Students whose login ID or roll number contains the text, at most 50. */
export async function searchStudentProfiles(q: string, signal?: AbortSignal) {
  const response = await apiClient.get<StudentProfileSummary[]>(`${PROFILES}/students`, { params: { q }, signal })
  return response.data
}

export async function updateStudentProfile(studentProfileId: number, body: { rollNumber: string | null; status: ActiveStatus }) {
  const response = await apiClient.put<StudentProfileSummary>(`${PROFILES}/students/${studentProfileId}`, body)
  return response.data
}

/** Faculty whose login ID contains the text, at most 50. */
export async function searchFacultyProfiles(q: string, signal?: AbortSignal) {
  const response = await apiClient.get<FacultyProfileSummary[]>(`${PROFILES}/faculty`, { params: { q }, signal })
  return response.data
}

export async function updateFacultyProfile(facultyProfileId: number, body: { departmentId: number; status: ActiveStatus }) {
  const response = await apiClient.put<FacultyProfileSummary>(`${PROFILES}/faculty/${facultyProfileId}`, body)
  return response.data
}
