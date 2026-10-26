import type {
  AdminCourseEnrollmentReportPage,
  AdminReportOverview,
  FacultyCourseReport,
  FacultyReportCourseSummary,
  ReportFilterQuery,
} from '../types/reports'
import { apiClient } from './apiClient'

// Reports read only. The account and its roles come from the access token that only the client sends; no request
// names a faculty member or an account.

export async function getFacultyReportCourses(signal?: AbortSignal) {
  return (await apiClient.get<FacultyReportCourseSummary[]>('/reports/faculty/my-courses', { signal })).data
}

export async function getFacultyCourseReport(courseOfferingId: number, signal?: AbortSignal) {
  return (await apiClient.get<FacultyCourseReport>(`/reports/faculty/course-offerings/${courseOfferingId}`, { signal })).data
}

export async function getAdminReportOverview(filters: ReportFilterQuery, signal?: AbortSignal) {
  return (await apiClient.get<AdminReportOverview>('/reports/admin/overview', { params: filters, signal })).data
}

export async function getAdminEnrollmentReport(filters: ReportFilterQuery, page: number, size: number, signal?: AbortSignal) {
  return (await apiClient.get<AdminCourseEnrollmentReportPage>('/reports/admin/enrollments', {
    params: { ...filters, page, size },
    signal,
  })).data
}

/** The enrollment report as a CSV file, under the same filters. The token travels in the header, never the address. */
export async function getAdminEnrollmentCsvExport(filters: ReportFilterQuery) {
  return (await apiClient.get<Blob>('/reports/admin/enrollments/export.csv', {
    params: filters,
    responseType: 'blob',
    headers: { Accept: 'text/csv' },
  })).data
}
