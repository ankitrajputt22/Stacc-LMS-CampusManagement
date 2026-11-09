import {
  getAttendanceSession,
  getAttendanceSessions,
  getMyAttendance,
  getMyAttendanceCourses,
} from '../api/attendanceApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'

/** The course offerings the signed-in faculty member teaches. The backend decides from teaching assignments. */
export function useMyAttendanceCourses() {
  return useApiQuery(queryKeys.attendance.myCourses(), getMyAttendanceCourses)
}

/** A course offering's sessions. refresh() reloads them quietly after a change; a null ID sends nothing. */
export function useAttendanceSessions(courseOfferingId: number | null) {
  return useApiQuery(courseOfferingId === null ? null : queryKeys.attendance.sessions(courseOfferingId), (signal) =>
    getAttendanceSessions(courseOfferingId ?? 0, signal),
  )
}

/** One session with its roster. A null ID sends nothing. */
export function useAttendanceSession(sessionId: number | null) {
  return useApiQuery(sessionId === null ? null : queryKeys.attendance.session(sessionId), (signal) =>
    getAttendanceSession(sessionId ?? 0, signal),
  )
}

/** The signed-in student's own finalized attendance. */
export function useMyAttendance() {
  return useApiQuery(queryKeys.attendance.mine(), getMyAttendance)
}
