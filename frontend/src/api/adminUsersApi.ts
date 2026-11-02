import type {
  AccountStatus,
  FacultyProfileCreate,
  RoleName,
  StudentProfileCreate,
  UserCreate,
  UserDetail,
  UserFilters,
  UserPage,
} from '../types/users'
import { apiClient } from './apiClient'

// Every route needs ADMIN with USER_VIEW, and USER_MANAGE to change anything; the backend checks both. The account
// changed is always named by the URL. Temporary passwords are sent once and never come back.

const ROOT = '/admin/users'

export async function searchUsers(filters: UserFilters, signal?: AbortSignal) {
  return (await apiClient.get<UserPage>(ROOT, { params: filters, signal })).data
}

export async function getUser(accountId: number, signal?: AbortSignal) {
  return (await apiClient.get<UserDetail>(`${ROOT}/${accountId}`, { signal })).data
}

export async function createUser(body: UserCreate) {
  return (await apiClient.post<UserDetail>(ROOT, body)).data
}

export async function updateUserDetails(accountId: number, fullName: string, email: string | null) {
  return (await apiClient.put<UserDetail>(`${ROOT}/${accountId}/details`, { fullName, email })).data
}

export async function updateUserStatus(accountId: number, status: AccountStatus) {
  return (await apiClient.put<UserDetail>(`${ROOT}/${accountId}/status`, { status })).data
}

export async function updateUserRoles(accountId: number, roles: RoleName[]) {
  return (await apiClient.put<UserDetail>(`${ROOT}/${accountId}/roles`, { roles })).data
}

export async function issueTemporaryPassword(accountId: number, temporaryPassword: string) {
  return (await apiClient.post<UserDetail>(`${ROOT}/${accountId}/temporary-password`, { temporaryPassword })).data
}

export async function addStudentProfile(accountId: number, body: StudentProfileCreate) {
  return (await apiClient.post<UserDetail>(`${ROOT}/${accountId}/student-profile`, body)).data
}

export async function addFacultyProfile(accountId: number, body: FacultyProfileCreate) {
  return (await apiClient.post<UserDetail>(`${ROOT}/${accountId}/faculty-profile`, body)).data
}
