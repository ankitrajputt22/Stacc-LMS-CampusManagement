import type {
  AttendanceCourseOffering,
  AttendanceRecordChange,
  AttendanceSession,
  AttendanceSessionDetail,
  AttendanceSessionInput,
  StudentAttendance,
} from '../types/attendance'
import { apiClient } from './apiClient'

export async function getMyAttendanceCourses(signal?: AbortSignal): Promise<AttendanceCourseOffering[]> {
  const response = await apiClient.get<AttendanceCourseOffering[]>('/attendance/faculty/my-courses', { signal })
  return response.data
}

export async function getAttendanceSessions(courseOfferingId: number, signal?: AbortSignal): Promise<AttendanceSession[]> {
  const response = await apiClient.get<AttendanceSession[]>(
    `/attendance/faculty/course-offerings/${courseOfferingId}/sessions`,
    { signal },
  )
  return response.data
}

export async function createAttendanceSession(
  courseOfferingId: number,
  input: AttendanceSessionInput,
): Promise<AttendanceSession> {
  const response = await apiClient.post<AttendanceSession>(
    `/attendance/faculty/course-offerings/${courseOfferingId}/sessions`,
    input,
  )
  return response.data
}

export async function getAttendanceSession(sessionId: number, signal?: AbortSignal): Promise<AttendanceSessionDetail> {
  const response = await apiClient.get<AttendanceSessionDetail>(`/attendance/faculty/sessions/${sessionId}`, { signal })
  return response.data
}

export async function updateAttendanceSession(sessionId: number, input: AttendanceSessionInput): Promise<AttendanceSession> {
  const response = await apiClient.put<AttendanceSession>(`/attendance/faculty/sessions/${sessionId}`, input)
  return response.data
}

export async function saveAttendanceRecords(
  sessionId: number,
  records: AttendanceRecordChange[],
): Promise<AttendanceSessionDetail> {
  const response = await apiClient.put<AttendanceSessionDetail>(`/attendance/faculty/sessions/${sessionId}/records`, {
    records,
  })
  return response.data
}

export async function finalizeAttendanceSession(sessionId: number): Promise<AttendanceSession> {
  const response = await apiClient.post<AttendanceSession>(`/attendance/faculty/sessions/${sessionId}/finalize`)
  return response.data
}

export async function getMyAttendance(signal?: AbortSignal): Promise<StudentAttendance> {
  const response = await apiClient.get<StudentAttendance>('/attendance/student/me', { signal })
  return response.data
}
