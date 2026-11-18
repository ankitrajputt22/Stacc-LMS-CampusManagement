export type AttendanceSessionStatus = 'OPEN' | 'FINALIZED'

export type AttendanceStatus = 'UNMARKED' | 'PRESENT' | 'ABSENT'

export type AttendanceCourseOffering = {
  courseOfferingId: number
  courseCode: string
  courseName: string
  credits: number
  semesterNumber: number
  programCode: string
  academicSessionCode: string
}

export type AttendanceSession = {
  id: number
  heldAt: string
  topic: string | null
  status: AttendanceSessionStatus
  finalizedAt: string | null
}

export type AttendanceRosterRecord = {
  attendanceRecordId: number
  studentLoginId: string
  status: AttendanceStatus
  markedAt: string | null
}

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

export type AttendanceSessionInput = {
  heldAt: string
  topic: string | null
}

export type AttendanceRecordChange = {
  attendanceRecordId: number
  status: AttendanceStatus
}

export type StudentAttendanceSession = {
  attendanceSessionId: number
  heldAt: string
  topic: string | null
  status: 'PRESENT' | 'ABSENT'
  attendanceRecordId: number
}

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
  attendancePercentage: number | null
  sessions: StudentAttendanceSession[]
  lowAttendance: boolean
}

export type StudentAttendance = {
  courses: StudentCourseAttendance[]
}
