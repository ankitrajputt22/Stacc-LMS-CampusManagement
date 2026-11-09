import type {
  AttendanceCourseOffering,
  AttendanceRecordChange,
  AttendanceSession,
  AttendanceSessionDetail,
  AttendanceSessionInput,
  StudentAttendance,
} from '../types/attendance'
import { apiClient } from './apiClient'

// The client's base URL already ends in /api. The faculty member or student is the one in the access token;
// no faculty, student, profile, or enrollment ID is ever sent from here.

/** The course offerings the signed-in faculty member takes attendance for. */
export async function getMyAttendanceCourses(signal?: AbortSignal): Promise<AttendanceCourseOffering[]> {
  const response = await apiClient.get<AttendanceCourseOffering[]>('/attendance/faculty/my-courses', { signal })
  return response.data
}

/** A course offering's sessions, the latest class first. */
export async function getAttendanceSessions(courseOfferingId: number, signal?: AbortSignal): Promise<AttendanceSession[]> {
  const response = await apiClient.get<AttendanceSession[]>(
    `/attendance/faculty/course-offerings/${courseOfferingId}/sessions`,
    { signal },
  )
  return response.data
}

/** Creates an open session. The backend puts the students enrolled now on its roster. */
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

/** One session with its roster. */
export async function getAttendanceSession(sessionId: number, signal?: AbortSignal): Promise<AttendanceSessionDetail> {
  const response = await apiClient.get<AttendanceSessionDetail>(`/attendance/faculty/sessions/${sessionId}`, { signal })
  return response.data
}

/** Changes an open session's date and topic. */
export async function updateAttendanceSession(sessionId: number, input: AttendanceSessionInput): Promise<AttendanceSession> {
  const response = await apiClient.put<AttendanceSession>(`/attendance/faculty/sessions/${sessionId}`, input)
  return response.data
}

/** Saves statuses by record ID and answers with the whole session. */
export async function saveAttendanceRecords(
  sessionId: number,
  records: AttendanceRecordChange[],
): Promise<AttendanceSessionDetail> {
  const response = await apiClient.put<AttendanceSessionDetail>(`/attendance/faculty/sessions/${sessionId}/records`, {
    records,
  })
  return response.data
}

/** Finalizes a session whose students are all marked. Students then see it. */
export async function finalizeAttendanceSession(sessionId: number): Promise<AttendanceSession> {
  const response = await apiClient.post<AttendanceSession>(`/attendance/faculty/sessions/${sessionId}/finalize`)
  return response.data
}

/** The signed-in student's own finalized attendance. */
export async function getMyAttendance(signal?: AbortSignal): Promise<StudentAttendance> {
  const response = await apiClient.get<StudentAttendance>('/attendance/student/me', { signal })
  return response.data
}
