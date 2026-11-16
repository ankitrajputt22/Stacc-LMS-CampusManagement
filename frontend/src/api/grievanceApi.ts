import type { GrievanceCase, GrievanceCategoryAdmin, GrievanceCategoryOption, GrievanceSummary } from '../types/grievances'
import { apiClient } from './apiClient'

// The complainant and the committee member are always the signed-in account; the backend checks each case.

export const getGrievanceCategories = async (signal?: AbortSignal) =>
  (await apiClient.get<GrievanceCategoryOption[]>('/grievances/categories', { signal })).data
export const submitGrievance = async (input: { categoryId: number; subject: string; details: string }) =>
  (await apiClient.post<GrievanceCase>('/grievances', input)).data
export const getMyGrievances = async (signal?: AbortSignal) => (await apiClient.get<GrievanceSummary[]>('/grievances/me', { signal })).data
export const getMyGrievance = async (caseId: number, signal?: AbortSignal) =>
  (await apiClient.get<GrievanceCase>(`/grievances/me/${caseId}`, { signal })).data
export const appealGrievance = async (caseId: number, text: string) => (await apiClient.post<GrievanceCase>(`/grievances/me/${caseId}/appeal`, { text })).data
export const acceptGrievance = async (caseId: number) => (await apiClient.post<GrievanceCase>(`/grievances/me/${caseId}/accept`)).data

export const getCommitteeGrievances = async (signal?: AbortSignal) =>
  (await apiClient.get<GrievanceSummary[]>('/grievances/committee', { signal })).data
export const getCommitteeGrievance = async (caseId: number, signal?: AbortSignal) =>
  (await apiClient.get<GrievanceCase>(`/grievances/committee/${caseId}`, { signal })).data
export const reviewGrievance = async (caseId: number) => (await apiClient.post<GrievanceCase>(`/grievances/committee/${caseId}/review`)).data
export const resolveGrievance = async (caseId: number, text: string) =>
  (await apiClient.post<GrievanceCase>(`/grievances/committee/${caseId}/resolve`, { text })).data
export const decideGrievanceAppeal = async (caseId: number, text: string) =>
  (await apiClient.post<GrievanceCase>(`/grievances/committee/${caseId}/appeal-decision`, { text })).data

export const getAdminGrievanceCategories = async (signal?: AbortSignal) =>
  (await apiClient.get<GrievanceCategoryAdmin[]>('/admin/grievances/categories', { signal })).data
export const createGrievanceCategory = async (input: { code: string; name: string; responseDays: number }) =>
  (await apiClient.post<GrievanceCategoryAdmin>('/admin/grievances/categories', input)).data
export const changeGrievanceCategory = async (categoryId: number, input: { name: string; responseDays: number; active: boolean }) =>
  (await apiClient.put<GrievanceCategoryAdmin>(`/admin/grievances/categories/${categoryId}`, input)).data
export const addGrievanceCommitteeMember = async (categoryId: number, loginId: string) =>
  (await apiClient.post<GrievanceCategoryAdmin>(`/admin/grievances/categories/${categoryId}/committee`, { loginId })).data
export const endGrievanceCommitteeMember = async (memberId: number) =>
  (await apiClient.post<GrievanceCategoryAdmin>(`/admin/grievances/committee/${memberId}/end`)).data
