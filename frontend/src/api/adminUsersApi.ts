import type {
  ImportResult,
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

/** An account's official photo as image data, for an object URL. */
export async function getUserPhoto(accountId: number, signal?: AbortSignal) {
  return (await apiClient.get<Blob>(`${ROOT}/${accountId}/photo`, { responseType: 'blob', signal })).data
}

/** Sets the official photo: a JPEG or PNG image up to 2 MB. */
export async function uploadUserPhoto(accountId: number, file: File) {
  const body = new FormData()
  body.append('file', file)
  return (await apiClient.post<UserDetail>(`${ROOT}/${accountId}/photo`, body)).data
}

/**
 * Checks a CSV file of accounts (dryRun) or imports it. Every account gets the one temporary password sent here;
 * nothing is created unless every row is valid.
 */
export async function importUsers(file: File, temporaryPassword: string, dryRun: boolean) {
  const body = new FormData()
  body.append('file', file)
  body.append('temporaryPassword', new Blob([temporaryPassword], { type: 'text/plain' }))
  return (await apiClient.post<ImportResult>(`${ROOT}/import`, body, { params: { dryRun } })).data
}
