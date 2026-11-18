import {
  getAttendanceSession,
  getAttendanceSessions,
  getMyAttendance,
  getMyAttendanceCourses,
} from '../api/attendanceApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'

export function useMyAttendanceCourses() {
  return useApiQuery(queryKeys.attendance.myCourses(), getMyAttendanceCourses)
}

export function useAttendanceSessions(courseOfferingId: number | null) {
  return useApiQuery(courseOfferingId === null ? null : queryKeys.attendance.sessions(courseOfferingId), (signal) =>
    getAttendanceSessions(courseOfferingId ?? 0, signal),
  )
}

export function useAttendanceSession(sessionId: number | null) {
  return useApiQuery(sessionId === null ? null : queryKeys.attendance.session(sessionId), (signal) =>
    getAttendanceSession(sessionId ?? 0, signal),
  )
}

export function useMyAttendance() {
  return useApiQuery(queryKeys.attendance.mine(), getMyAttendance)
}
