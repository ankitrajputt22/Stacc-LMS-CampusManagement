import { useMemo } from 'react'
import {
  getAttendanceSession,
  getAttendanceSessions,
  getMyAttendance,
  getMyAttendanceCourses,
} from '../api/attendanceApi'
import { useApiRequest } from '../api/useApiRequest'

/** The course offerings the signed-in faculty member teaches. The backend decides from teaching assignments. */
export function useMyAttendanceCourses() {
  return useApiRequest(getMyAttendanceCourses)
}

/** A course offering's sessions. refresh() reloads them quietly after a change; a null ID sends nothing. */
export function useAttendanceSessions(courseOfferingId: number | null) {
  const request = useMemo(
    () => (courseOfferingId === null ? null : (signal: AbortSignal) => getAttendanceSessions(courseOfferingId, signal)),
    [courseOfferingId],
  )
  return useApiRequest(request)
}

/** One session with its roster. A null ID sends nothing. */
export function useAttendanceSession(sessionId: number | null) {
  const request = useMemo(
    () => (sessionId === null ? null : (signal: AbortSignal) => getAttendanceSession(sessionId, signal)),
    [sessionId],
  )
  return useApiRequest(request)
}

/** The signed-in student's own finalized attendance. */
export function useMyAttendance() {
  return useApiRequest(getMyAttendance)
}
