// Attendance is official academic data of a course offering. It never involves an LMS course.

export type AttendanceSessionStatus = 'OPEN' | 'FINALIZED'

/** UNMARKED means not marked yet; it never means absent. */
export type AttendanceStatus = 'UNMARKED' | 'PRESENT' | 'ABSENT'

/** A course offering the faculty member teaches, from GET /api/attendance/faculty/my-courses. */
export type AttendanceCourseOffering = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
}

/** One session in a course offering's list. */
export type AttendanceSession = {
  id: number
  /** When the class was held, as an ISO 8601 time in UTC. */
  heldAt: string
  /** Plain text, or null. */
  topic: string | null
  status: AttendanceSessionStatus
  /** When it was first finalized, or null while open. */
  finalizedAt: string | null
}

/** One student on a session's roster. The student is shown by college ID only. */
export type AttendanceRosterRecord = {
  attendanceRecordId: number
  studentLoginId: string
  status: AttendanceStatus
  markedAt: string | null
}

/** One session with its whole roster, in student ID order. */
export type AttendanceSessionDetail = {
  sessionId: number
  courseOfferingId: number
  courseCode: string
  courseName: string
  heldAt: string
  topic: string | null
  status: AttendanceSessionStatus
  finalizedAt: string | null
  records: AttendanceRosterRecord[]
}

/** When a class was held and its topic, for creating or changing a session. */
export type AttendanceSessionInput = {
  /** An absolute moment, such as 2026-10-19T04:30:00.000Z. */
  heldAt: string
  topic: string | null
}

/** One status to save, for the record with this ID. */
export type AttendanceRecordChange = {
  attendanceRecordId: number
  status: AttendanceStatus
}

/** One finalized session in a student's own history. */
export type StudentAttendanceSession = {
  attendanceSessionId: number
  heldAt: string
  topic: string | null
  status: 'PRESENT' | 'ABSENT'
}

/** A student's finalized attendance in one course offering, from GET /api/attendance/student/me. */
export type StudentCourseAttendance = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  semesterNumber: number
  programCode: string
  academicSessionCode: string
  totalSessions: number
  presentCount: number
  absentCount: number
  /** Calculated by the backend to two decimal places, such as 83.33. Null only if there were no sessions. */
  attendancePercentage: number | null
  /** Latest class first. */
  sessions: StudentAttendanceSession[]
}

export type StudentAttendance = {
  /** Only course offerings with at least one finalized session. */
  courses: StudentCourseAttendance[]
}
