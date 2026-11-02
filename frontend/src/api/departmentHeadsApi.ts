import type { DepartmentHeadCandidate, DepartmentHeadRow, DepartmentHeadTerm } from '../types/departmentHeads'
import { apiClient } from './apiClient'

// Every route needs the admin role and the department head permissions; the backend checks both on every request and
// takes the acting admin from the session, never from the body.

const ROOT = '/admin/academic'

export async function getDepartmentHeads(signal?: AbortSignal): Promise<DepartmentHeadRow[]> {
  return (await apiClient.get<DepartmentHeadRow[]>(`${ROOT}/department-heads`, { signal })).data
}

export async function getDepartmentHeadCandidates(signal?: AbortSignal): Promise<DepartmentHeadCandidate[]> {
  return (await apiClient.get<DepartmentHeadCandidate[]>(`${ROOT}/department-heads/candidates`, { signal })).data
}

export async function getDepartmentHeadHistory(departmentId: number, signal?: AbortSignal): Promise<DepartmentHeadTerm[]> {
  return (await apiClient.get<DepartmentHeadTerm[]>(`${ROOT}/departments/${departmentId}/heads`, { signal })).data
}

/** Makes the account the department's head; the current head's term ends first. */
export async function assignDepartmentHead(departmentId: number, userAccountId: number): Promise<DepartmentHeadRow> {
  return (await apiClient.put<DepartmentHeadRow>(`${ROOT}/departments/${departmentId}/head`, { userAccountId })).data
}

/** Ends the current head's term, which stays in the history. */
export async function endDepartmentHead(departmentId: number): Promise<DepartmentHeadRow> {
  return (await apiClient.post<DepartmentHeadRow>(`${ROOT}/departments/${departmentId}/head/end`)).data
}
