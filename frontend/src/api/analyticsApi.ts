import type { AreaAnalytics, CourseAnalytics, DepartmentAnalytics, ExportKind, ReportExport, StudentAnalytics } from '../types/analytics'
import { apiClient } from './apiClient'
import { FILE_TIMEOUT_MS } from './lmsContentApi'

export const getMyAnalytics = async (signal?: AbortSignal) => (await apiClient.get<StudentAnalytics>('/analytics/student/me', { signal })).data
export const getCourseAnalytics = async (lmsCourseId: number, signal?: AbortSignal) =>
  (await apiClient.get<CourseAnalytics>(`/analytics/faculty/courses/${lmsCourseId}`, { signal })).data
export const startExport = async (kind: ExportKind, parameter: number | null) =>
  (await apiClient.post<ReportExport>('/exports', { kind, parameter })).data
export const getMyExports = async (signal?: AbortSignal) => (await apiClient.get<ReportExport[]>('/exports', { signal })).data
export const downloadExport = async (exportId: number) =>
  (await apiClient.get<Blob>(`/exports/${exportId}/download`, { responseType: 'blob', timeout: FILE_TIMEOUT_MS })).data

/** The departments the signed-in head of department heads now, with current-session counts. */
export const getDepartmentAnalytics = async (signal?: AbortSignal) =>
  (await apiClient.get<DepartmentAnalytics[]>('/analytics/department', { signal })).data

/** Area analytics; each section only for holders of that area's permission. */
export const getAreaAnalytics = async (signal?: AbortSignal) => (await apiClient.get<AreaAnalytics>('/analytics/areas', { signal })).data
